package net.perelengo.reverse.jamopp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.RuntimeErrorException;

import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.plugin.EcorePlugin.ExtensionProcessor;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.emftext.language.java.JavaClasspath;
import org.emftext.language.java.LogicalJavaURIGenerator;
import org.emftext.language.java.containers.CompilationUnit;
import org.emftext.language.java.containers.JavaRoot;
import org.emftext.language.java.containers.Package;
import org.emftext.language.java.resolver.CentralReferenceResolver;

import jamopp.options.ParserOptions;
import jamopp.parser.jdt.singlefile.JaMoPPJDTSingleFileParser;
import jamopp.resolution.bindings.CentralBindingBasedResolver;
import jamopp.resource.JavaResource2Factory;

public class Parser {
	private static Path outputPath=null;
			
	public static void main(String[] args) {
		String outputFilePath="out";
		File file = new File(outputFilePath).getAbsoluteFile();
		file.getAbsoluteFile().mkdirs();
		
		outputPath=file.getAbsoluteFile().toPath();
		
		new Parser().parse();
	}
	private ResourceSetImpl rs;
	

	private void parse() {
		ParserOptions.CREATE_LAYOUT_INFORMATION.setValue(Boolean.TRUE);
		ParserOptions.REGISTER_LOCAL.setValue(Boolean.FALSE);
		//ParserOptions.RESOLVE_ALL_BINDINGS.setValue(Boolean.TRUE);
		ParserOptions.RESOLVE_BINDINGS.setValue(Boolean.TRUE);
		ParserOptions.RESOLVE_BINDINGS_OF_INFERABLE_TYPES.setValue(Boolean.TRUE);
		ParserOptions.RESOLVE_EVERYTHING.setValue(Boolean.TRUE);
		ParserOptions.PREFER_BINDING_CONVERSION.setValue(Boolean.TRUE);
		
		initResourceFactory();
		createNewResourceSet();
		
		JaMoPPJDTSingleFileParser parser = new JaMoPPJDTSingleFileParser();
		parser.setResourceSet(getResourceSet());
		registerPackagesStandalone(getResourceSet());
		
		try {
			String[] xmiFiles = findFiles(outputPath, "xmi");
			for (String file : xmiFiles) {
				URI fileUri = URI.createFileURI(file);
				Resource fileResource = getResourceSet().createResource(fileUri);
				fileResource.load(getLoadOptions());
				JavaClasspath.get().registerJavaRoot((JavaRoot) fileResource.getContents().get(0),fileUri);
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		ResourceSet parsedRs = parser.parseDirectory(Paths.get("C:\\dev\\EMA\\CT2\\Projects\\ClinicalTrialWorkspaces\\ema-ct"));

//		EcoreUtil.resolveAll(getResourceSet());
				
//		for (Resource res : new ArrayList<Resource>(getResourceSet().getResources())) {
//			try {
//				this.resolveAllProxies(res);
//			}catch(Exception e) {
//				e.printStackTrace();
//			}
//		}

		ResourceSet targetSet = transferToXMI(getResourceSet(), true);
	}
	
	public final void initResourceFactory() {
		this.createNewResourceSet();
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("java", new JavaResource2Factory());
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl() {
			 @Override
			  public Resource createResource(URI uri)
			  {
			    return new XMIResourceImpl(uri) {
			    	@Override
					  protected boolean useUUIDs() {
						  return true;
					  }

			    };
			  }
			 
		});
		
		JavaClasspath.get();
	}
	
	private void resolveAllProxies(Resource resource) {
			StringBuffer msg = new StringBuffer();
			resource.getAllContents().forEachRemaining(obj -> {
				InternalEObject element = (InternalEObject) obj;
				for (EObject crElement : element.eCrossReferences()) {
					crElement = EcoreUtil.resolve(crElement, resource);
					
					if (crElement.eIsProxy())
						crElement = EcoreUtil.resolve(crElement, resource);
					 
					if (crElement.eIsProxy()) {
						msg.append("\nCan not resolve: " + ((InternalEObject) crElement).eProxyURI());
					}
				}
			});
			
			String finalMsg = msg.toString();
			if(finalMsg.length() != 0) throw new RuntimeException(msg.toString());
	}

	private ResourceSet getResourceSet() {
		return rs;
	}
	private void createNewResourceSet() {
		rs = new ResourceSetImpl();
		rs.getLoadOptions().putAll(getLoadOptions());
		
	}
	protected Map<? extends Object, ? extends Object> getLoadOptions() {
		Map<Object, Object> map = new HashMap<Object, Object>();
		return map;
	}
	
	protected ResourceSet transferToXMI(ResourceSet sourceSet, boolean includeAllResources)  {
		int emptyFileName = 0;
		
		ResourceSet targetSet = new ResourceSetImpl();
		targetSet.getLoadOptions().put(XMIResource.OPTION_USE_XMI_TYPE, true);
		targetSet.getLoadOptions().put(XMIResource.OPTION_USE_ENCODED_ATTRIBUTE_STYLE, false);
		targetSet.getLoadOptions().put(XMIResource.OPTION_DEFER_ATTACHMENT, true);
		targetSet.getLoadOptions().put(XMIResource.OPTION_DEFER_IDREF_RESOLUTION, true);
		
		for (Resource javaResource : new ArrayList<>(sourceSet.getResources())) {
			if (javaResource.getContents().isEmpty()) {
				//System.out.println("WARNING: Emtpy Resource: " + javaResource.getURI());
				continue;
			}
			if (!includeAllResources && !javaResource.getURI().isFile()) {
				continue;
			}
			JavaRoot root = (JavaRoot) javaResource.getContents().get(0);
			String outputFileName = "ERROR";
			if (root instanceof CompilationUnit) {
				outputFileName = root.getNamespacesAsString().replace(".", File.separator) + File.separator;
				CompilationUnit cu = (CompilationUnit) root;
				if (cu.getClassifiers().size() > 0) {
					outputFileName += cu.getClassifiers().get(0).getName();
				} else {
					outputFileName += emptyFileName++;
				}

			} else if (root instanceof Package) {
				outputFileName = root.getNamespacesAsString()
						.replace(".", File.separator) + File.separator + "package-info";
				if (outputFileName.startsWith(File.separator)) {
					outputFileName = outputFileName.substring(1);
				}
			} else if (root instanceof org.emftext.language.java.containers.Module) {
				outputFileName = root.getNamespacesAsString()
						.replace(".", File.separator) + File.separator + "module-info";
			} else {
				throw new RuntimeException("unknown type "+ root.getClass().getCanonicalName());
			}
			File outputFile = new File(outputPath.toFile().getAbsolutePath()
					+ File.separator + outputFileName);
			System.out.println("generating "+outputFile.getAbsolutePath());
			URI xmiFileURI = URI.createFileURI(outputFile.getAbsolutePath()).appendFileExtension("xmi");	
			XMIResource xmiResource = (XMIResource) targetSet.createResource(xmiFileURI);
			xmiResource.setEncoding(StandardCharsets.UTF_8.toString());
			xmiResource.getContents().addAll(javaResource.getContents());
		}
		for (Resource xmiResource : targetSet.getResources()) {
			try {
				xmiResource.save(targetSet.getLoadOptions());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return targetSet;
	}
	
    public void registerPackagesStandalone(ResourceSet resourceSet) {
        Map uriMap = resourceSet.getURIConverter().getURIMap();
        	
        	//Some times the classpath of the classloader is not enough to resolve all classpath plugins.
        	//An example of this is when classpath is referenced through one of the jar's MANIFEST.MF Class-Path directive.
        	//To detect this issue, try to find the ResourcesPlugin.class in the contextClassloaderClasspath
        	//if the containing jar is not found in the classloader classpath URLs,
        	//then use the java.class.path system property and join both classpaths
        	//java.class.path system property is not used by default due to some limitations for example when exporting this project as runnable jar from eclipse with all dependencies bundled in the jar, because the jar handles the classpath in a special incompatible way.....
        	
        	String[] cpFiles=null;
        	
        	ClassLoader classLoader=(Thread.currentThread().getContextClassLoader());
            ExtensionProcessor.process(classLoader);
            	
            String classpath = null;
            classpath = System.getProperty("java.class.path");
            String[] cpFiles1 = (classpath.indexOf(";")!=-1)?classpath.split(";"):classpath.split(":"); //; for windows : for linux
            
            boolean extendClassLoaderClassPath=true;
            try
            {
            	URL[] ucp=(classLoader instanceof URLClassLoader)?((URLClassLoader)classLoader).getURLs():new URL[] {};
        		String[] cpFiles2 = new String[ucp.length];
            	
        		//Path of the jar containing the ResourcesPlugin.class
            	String resourcesPluginJarPath=this.getClass().getClassLoader().getResource(this.getClass().getCanonicalName().replaceAll("\\.","/")+".class").getFile();
            	
            	//fetch ResourcesPlugin.class containing jar in classloader classpath urls
            	for (int i=0;i<ucp.length;i++) {
            		//if found, then won't use system property classpath
					if(ucp[i].toString().equals(resourcesPluginJarPath))
						extendClassLoaderClassPath=false;
				}
            	for (int i=0;i<ucp.length;i++) {
					cpFiles2[i]=ucp[i].toString();
            	}
            	
            	
            	
            	if(extendClassLoaderClassPath)
            		cpFiles=new String[cpFiles1.length+cpFiles2.length];
            	else
            		cpFiles=new String[cpFiles2.length];
            	
            	for (int i=0;i<cpFiles2.length;i++) {
					cpFiles[i]=ucp[i].toString();
            	}
            	if(extendClassLoaderClassPath){
	                for (int i=0;i<cpFiles1.length;i++) {
						cpFiles[i+cpFiles2.length]=cpFiles1[i];
						
					}
            	}
            }
            catch (Throwable throwable)
            {
              // Failing thet, get it from the system properties.
              throwable.printStackTrace();
              
            }
        	
	        
	        for (String filePath : cpFiles) {
	        	try {
		        	if(!filePath.contains(System.getProperty("java.home"))) {
		        		System.err.println("Loading "+filePath);
		        		JavaClasspath.get().registerZip(URI.createFileURI(filePath));
		        	}else {
		        		System.err.println("Not loading "+filePath);
		        	}
	        	}catch(Exception e) {
	        		e.printStackTrace();
	        	}
	        }     
    }
    
    private String[] findFiles(Path directory, String...extensions) throws IOException {
		return Files.walk(directory).filter(path -> Files.isRegularFile(path)
				&& testFileExtensions(path, extensions))
				.map(Path::toAbsolutePath).map(Path::toString)
				.map(s -> s.replace(File.separator, "/"))
				.toArray(i -> new String[i]);
	}
    
	private boolean testFileExtensions(Path file, String...extensions) {
		String fileName = file.getFileName().toString();
		boolean result = false;
		for (String ext : extensions) {
			result = result || fileName.endsWith(ext);
		}
		return result;
	}
	
}
