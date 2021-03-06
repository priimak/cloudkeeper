package com.svbio.cloudkeeper.maven;

import com.svbio.cloudkeeper.dsl.AnnotationTypePlugin;
import com.svbio.cloudkeeper.dsl.CompositeModulePlugin;
import com.svbio.cloudkeeper.dsl.DSLPluginDescriptor;
import com.svbio.cloudkeeper.dsl.ModuleFactory;
import com.svbio.cloudkeeper.dsl.SerializationPlugin;
import com.svbio.cloudkeeper.dsl.SimpleModulePlugin;
import com.svbio.cloudkeeper.dsl.TypePlugin;
import com.svbio.cloudkeeper.linker.Linker;
import com.svbio.cloudkeeper.linker.LinkerOptions;
import com.svbio.cloudkeeper.model.LinkerException;
import com.svbio.cloudkeeper.model.beans.element.MutableBundle;
import com.svbio.cloudkeeper.model.beans.element.MutablePackage;
import com.svbio.cloudkeeper.model.immutable.element.Name;
import com.svbio.cloudkeeper.model.immutable.element.Version;
import com.svbio.cloudkeeper.model.runtime.element.RuntimeRepository;
import com.svbio.cloudkeeper.model.util.BuildInformation;
import com.svbio.cloudkeeper.model.util.ImmutableList;
import com.svbio.cloudkeeper.relocated.org.objectweb.asm.AnnotationVisitor;
import com.svbio.cloudkeeper.relocated.org.objectweb.asm.ClassReader;
import com.svbio.cloudkeeper.relocated.org.objectweb.asm.ClassVisitor;
import com.svbio.cloudkeeper.relocated.org.objectweb.asm.Opcodes;
import com.svbio.cloudkeeper.relocated.org.objectweb.asm.Type;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scans the build output directory for CloudKeeper plug-in declarations, creates a CloudKeeper bundle, verifies and
 * links the content, and writes out the bundle as XML.
 */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.PACKAGE)
public final class CompileBundleMojo extends AbstractMojo {
    /**
     * The standard Maven distribution, from Maven 3.1.0 onward, uses the SLF4J API for logging.
     *
     * @see <a href="http://maven.apache.org/maven-logging.html">http://maven.apache.org/maven-logging.html</a>
     */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     */
    @Component
    private RepositorySystem repositorySystem;

    /**
     * Helper class to assist in attaching artifacts.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Helper class to assist in resolving dependencies.
     */
    @Component
    private ProjectDependenciesResolver resolver;

    /**
     * The directory where all files generated by the build are placed.
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    private File buildDirectory;

    /**
     * The directory where compiled application classes are placed.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private File buildOutputDirectory;

    /**
     * The name of the file where the XML-serialized representation of the bundle will be written.
     */
    @Parameter(defaultValue = "${project.build.finalName}" + '.' + Bundles.ARTIFACT_TYPE, required = true)
    private String bundleFileName;

    /**
     * Project instance.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    /**
     * The project's remote repositories to use for the resolution of dependencies.
     */
    @Parameter(defaultValue = "${project.remoteRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepos;


    private static final List<Class<? extends Annotation>> RELEVANT_ANNOTATIONS
            = Collections.unmodifiableList(Arrays.asList(
        AnnotationTypePlugin.class, CompositeModulePlugin.class, SerializationPlugin.class,  SimpleModulePlugin.class,
        TypePlugin.class
    ));

    private static final String CLASS_SUFFIX = ".class";
    private static final String JAR_ARTIFACT_TYPE = "jar";
    private static final List<String> CLASSPATH_JAVA_SCOPES = Collections.unmodifiableList(Arrays.asList(
        JavaScopes.COMPILE, JavaScopes.PROVIDED, JavaScopes.RUNTIME, JavaScopes.SYSTEM
    ));


    private static final Map<String, Class<? extends Annotation>> DESCRIPTOR_MAP;

    static {
        Map<String, Class<? extends Annotation>> descriptorMap = new HashMap<>();
        for (Class<? extends Annotation> cloudKeeperAnnotation: RELEVANT_ANNOTATIONS) {
            descriptorMap.put(Type.getDescriptor(cloudKeeperAnnotation), cloudKeeperAnnotation);
        }
        DESCRIPTOR_MAP = Collections.unmodifiableMap(descriptorMap);
    }

    /**
     * Returns the Aether repository system.
     *
     * <p>This method is only intended to be called during tests.
     */
    RepositorySystem getRepositorySystem() {
        return repositorySystem;
    }

    /**
     * Returns the Maven project.
     *
     * <p>This method is only intended to be called during tests.
     */
    MavenProject getProject() {
        return project;
    }

    /**
     * Returns the name of the file where the XML-serialized representation of the bundle will be written.
     *
     * <p>This method is only intended to be called during tests.
     */
    String getBundleFileName() {
        return bundleFileName;
    }

    /**
     * Sets the build directory.
     *
     * <p>This method is only intended to be called during tests.
     * @see #buildDirectory
     */
    void setBuildDirectory(Path buildDirectory) {
        this.buildDirectory = buildDirectory.toFile();
    }

    /**
     * Sets the Aether repository session.
     *
     * <p>This method is only intended to be called during tests.
     *
     * @see <a href="https://wiki.eclipse.org/Aether/Creating_a_Repository_System_Session">https://wiki.eclipse.org/Aether/Creating_a_Repository_System_Session</a>
     */
    void setRepositorySystemSession(RepositorySystemSession repositorySystemSession) {
        this.repositorySystemSession = repositorySystemSession;
    }

    /**
     * Scans the build output directory for CloudKeeper plug-in declarations, creates a CloudKeeper bundle, verifies and
     * links the content, and writes out the bundle as XML.
     *
     * @throws MojoExecutionException If an unexpected problem occurs. This exception causes a "BUILD ERROR" message to
     *     be displayed.
     * @throws MojoFailureException If an expected problem (such as a compilation failure) occurs. Throwing this
     *     exception causes a "BUILD FAILURE" message to be displayed.
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(MutableBundle.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            log.debug("Resolving dependencies...");
            DependencyResolutionResult dependencyResolution
                = resolver.resolve(new DefaultDependencyResolutionRequest(project, repositorySystemSession));
            log.debug("Dependencies: {}", dependencyResolution.getDependencies());

            List<URL> classpaths = getClasspaths(dependencyResolution);
            log.debug("Classpaths: {}", classpaths);
            URL[] classpathsArray = classpaths.toArray(new URL[classpaths.size()]);

            MutableBundle bundle;
            try (URLClassLoader classLoader
                    = URLClassLoader.newInstance(classpathsArray, getClass().getClassLoader())) {
                ModuleFactory moduleFactory = new ModuleFactory(classLoader);

                log.debug("Scanning build output directory '{}' for CloudKeeper plug-in declarations...",
                    buildOutputDirectory);
                MutableBundle mutableBundle = createBundle(moduleFactory);

                log.debug("Linking plug-in declarations in build output directory with bundle dependencies...");
                RuntimeRepository repository = link(mutableBundle, jaxbContext, dependencyResolution, classLoader);

                // Normalized representation!
                bundle = MutableBundle.copyOf(repository.getBundles().get(0));
            }

            // We do no store the bundle identifier explicitly, because it can be inferred from the Maven metadata.
            log.debug("Serializing CloudKeeper bundle as XML...");
            bundle.setBundleIdentifier(null);
            File bundleArtifactFile = new File(buildDirectory, bundleFileName);
            marshaller.marshal(bundle, bundleArtifactFile);

            log.debug("Attaching '{}' as artifact of type {}...", bundleArtifactFile, Bundles.ARTIFACT_TYPE);
            projectHelper.attachArtifact(project, Bundles.ARTIFACT_TYPE, bundleArtifactFile);
        } catch (LinkerException exception) {
            throw new MojoFailureException("The CloudKeeper bundle could not be linked.", exception);
        } catch (ClassNotFoundException | DependencyResolutionException | IOException | JAXBException
                | XMLStreamException exception) {
            throw new MojoExecutionException(
                "Tried to generate CloudKeeper bundle, but an exception occurred.", exception);
        }
    }

    /**
     * Links the given bundle together with all bundle dependencies.
     *
     * <p>The returned repository will have the linked runtime representation of the given bundle as first element of
     * {@link RuntimeRepository#getBundles()}.
     */
    private static RuntimeRepository link(MutableBundle bundle, JAXBContext jaxbContext,
            DependencyResolutionResult dependencyResolution, ClassLoader classLoader)
            throws JAXBException, LinkerException, XMLStreamException {
        List<MutableBundle> bundles = new ArrayList<>();
        bundles.add(bundle);
        bundles.addAll(loadDependencyBundles(jaxbContext, getBundleDependencies(dependencyResolution)));

        LinkerOptions linkerOptions = new LinkerOptions.Builder()
            .setClassProvider(name -> Optional.of(Class.forName(name.getBinaryName().toString(), true, classLoader)))
            .setDeserializeSerializationTrees(true)
            .setUnmarshalClassLoader(classLoader)
            .setMarshalValues(true)
            .build();
        return Linker.createRepository(bundles, linkerOptions);
    }

    private static MutablePackage getOrCreatePackage(Package javaPackage, Map<Name, MutablePackage> packageMap) {
        Name qualifiedName = Name.qualifiedName(javaPackage.getName());
        @Nullable MutablePackage thePackage = packageMap.get(qualifiedName);
        if (thePackage == null) {
            thePackage = MutablePackage.fromPackage(javaPackage);
            packageMap.put(qualifiedName, thePackage);
        }
        return thePackage;
    }

    /**
     * Returns a new bundle that contains all CloudKeeper plug-in declaration classes in the Maven build-output
     * directory.
     *
     * <p>The returned bundle will have a bundle identifier returned from
     * {@link Bundles#bundleIdentifierFromMaven(String, String, Version)}.
     *
     * @param moduleFactory module factory that will be used to load the given classes
     */
    private MutableBundle createBundle(ModuleFactory moduleFactory) throws ClassNotFoundException, IOException {
        List<String> classNames = getClassNames();
        Map<Name, MutablePackage> packageMap = new LinkedHashMap<>();
        for (String className: classNames) {
            DSLPluginDescriptor pluginDescriptor = moduleFactory.loadPluginDescriptor(className);
            Class<?> pluginClass = pluginDescriptor.getPluginClass();
            getOrCreatePackage(pluginClass.getPackage(), packageMap)
                .getDeclarations()
                .add(moduleFactory.loadDeclaration(pluginClass));
        }

        return new MutableBundle()
            .setBundleIdentifier(Bundles.bundleIdentifierFromMaven(
                project.getGroupId(), project.getArtifactId(), Version.valueOf(project.getVersion())
            ))
            .setCloudKeeperVersion(BuildInformation.PROJECT_VERSION)
            .setCreationTime(new Date())
            .setPackages(new ArrayList<>(packageMap.values()));
    }

    /**
     * Returns new {@link MutableBundle} instances for the given bundle artifacts.
     *
     * <p>Each {@link MutableBundle} will have a bundle identifier returned from
     * {@link Bundles#bundleIdentifierFromMaven(String, String, Version)}. The bundle identifier is inferred
     * from the Maven artifact, it is not part of the generated bundle XML.
     */
    private static List<MutableBundle> loadDependencyBundles(JAXBContext jaxbContext, List<Artifact> bundleArtifacts)
            throws JAXBException, XMLStreamException {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
        List<MutableBundle> bundles = new ArrayList<>();
        for (Artifact bundleArtifact: bundleArtifacts) {
            bundles.add(Bundles.loadBundle(jaxbContext, xmlInputFactory, bundleArtifact));
        }
        return bundles;
    }

    /**
     * Returns a list of bundle artifacts that the current Maven project (transitively) depends on.
     */
    private static List<Artifact> getBundleDependencies(DependencyResolutionResult dependencyResolutionResult) {
        List<Artifact> bundleArtifacts = new ArrayList<>();

        for (Dependency dependency: dependencyResolutionResult.getDependencies()) {
            Artifact artifact = dependency.getArtifact();
            if (artifact.getExtension().equals(Bundles.ARTIFACT_TYPE)
                    && CLASSPATH_JAVA_SCOPES.contains(dependency.getScope())) {
                bundleArtifacts.add(artifact);
            }
        }
        return bundleArtifacts;
    }

    /**
     * Returns a list of {@link URL}s that need to be in the classpath before executing any of the Java code in the
     * current project.
     */
    private List<URL> getClasspaths(DependencyResolutionResult dependencyResolutionResult)
            throws MalformedURLException {
        List<URL> artifactPaths = new ArrayList<>();

        artifactPaths.add(buildOutputDirectory.toURI().toURL());
        for (Dependency dependency: dependencyResolutionResult.getDependencies()) {
            Artifact artifact = dependency.getArtifact();
            if (artifact.getExtension().equals(JAR_ARTIFACT_TYPE)
                    && CLASSPATH_JAVA_SCOPES.contains(dependency.getScope())) {
                artifactPaths.add(artifact.getFile().toURI().toURL());
            }
        }
        return artifactPaths;
    }

    /**
     * Returns a list of class names that correspond to the class files in the Maven build-output directory (which is
     * typically {@code target/classes}).
     *
     * <p>This method only returns the names of those classes that have a CloudKeeper plug-in declaration annotation
     * (for instance, {@link SimpleModulePlugin}).
     */
    private ImmutableList<String> getClassNames() throws IOException {
        BuildOutputFileVisitor visitor = new BuildOutputFileVisitor(buildOutputDirectory.toPath());
        Files.walkFileTree(buildOutputDirectory.toPath(), visitor);
        return ImmutableList.copyOf(visitor.classNames);
    }

    private static final class BuildOutputFileVisitor extends SimpleFileVisitor<Path> {
        private final Logger log = LoggerFactory.getLogger(getClass());
        private final List<String> classNames = new ArrayList<>();
        private final Path basePath;

        private BuildOutputFileVisitor(Path basePath) {
            this.basePath = basePath;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            boolean isClassFile = file.getFileName().toString().endsWith(CLASS_SUFFIX);
            Path relativePath = basePath.relativize(file);
            @Nullable List<Class<? extends Annotation>> relevantAnnotationTypes = null;
            @Nullable String className = null;

            if (isClassFile) {
                try (InputStream inputStream = Files.newInputStream(file)) {
                    ClassReader classReader = new ClassReader(inputStream);
                    RelevantAnnotationTypesClassVisitor visitor = new RelevantAnnotationTypesClassVisitor();
                    classReader.accept(
                        visitor,
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
                    );
                    relevantAnnotationTypes = visitor.relevantAnnotationTypes;
                }

                if (!relevantAnnotationTypes.isEmpty()) {
                    String pathAsString = relativePath.toString();
                    className = pathAsString.substring(0, pathAsString.length() - CLASS_SUFFIX.length())
                        .replace(file.getFileSystem().getSeparator(), ".");

                    classNames.add(className);
                }
            }

            log.debug("Found file '{}' (is class file: {}, relevant plug-in annotation types: {}, class name: {})",
                relativePath, isClassFile, relevantAnnotationTypes, className);

            return FileVisitResult.CONTINUE;
        }
    }

    private static final class RelevantAnnotationTypesClassVisitor extends ClassVisitor {
        private final List<Class<? extends Annotation>> relevantAnnotationTypes = new ArrayList<>();

        private RelevantAnnotationTypesClassVisitor() {
            super(Opcodes.ASM4);
        }

        @Override
        @Nullable
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            @Nullable Class<? extends Annotation> annotationType = DESCRIPTOR_MAP.get(desc);
            if (annotationType != null) {
                relevantAnnotationTypes.add(annotationType);
            }
            return null;
        }
    }
}
