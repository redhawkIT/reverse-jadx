package jadx.tests.api;

import jadx.api.DefaultJadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JadxInternalAccess;
import jadx.core.Jadx;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.visitors.DepthTraversal;
import jadx.core.dex.visitors.IDexTreeVisitor;
import jadx.core.utils.exceptions.JadxException;
import jadx.core.utils.files.FileUtils;
import jadx.tests.api.compiler.DynamicCompiler;
import jadx.tests.api.utils.TestUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarOutputStream;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class IntegrationTest extends TestUtils {

	protected boolean outputCFG = false;
	protected boolean isFallback = false;
	protected boolean deleteTmpFiles = true;

	protected String outDir = "test-out-tmp";

	protected boolean compile = true;
	private DynamicCompiler dynamicCompiler;

	public ClassNode getClassNode(Class<?> clazz) {
		try {
			File jar = getJarForClass(clazz);
			return getClassNodeFromFile(jar, clazz.getName());
		} catch (Exception e) {
			fail(e.getMessage());
		}
		return null;
	}

	public ClassNode getClassNodeFromFile(File file, String clsName) {
		JadxDecompiler d = new JadxDecompiler();
		try {
			d.loadFile(file);
		} catch (JadxException e) {
			fail(e.getMessage());
		}
		ClassNode cls = JadxInternalAccess.getRoot(d).searchClassByName(clsName);
		assertNotNull("Class not found: " + clsName, cls);
		assertEquals(cls.getFullName(), clsName);

		cls.load();
		for (IDexTreeVisitor visitor : getPasses()) {
			DepthTraversal.visit(visitor, cls);
		}
		// don't unload class

		checkCode(cls);
		compile(cls);
		return cls;
	}

	private static void checkCode(ClassNode cls) {
		assertTrue("Inconsistent cls: " + cls,
				!cls.contains(AFlag.INCONSISTENT_CODE) && !cls.contains(AType.JADX_ERROR));
		for (MethodNode mthNode : cls.getMethods()) {
			assertTrue("Inconsistent method: " + mthNode,
					!mthNode.contains(AFlag.INCONSISTENT_CODE) && !mthNode.contains(AType.JADX_ERROR));
		}
		assertThat(cls.getCode().toString(), not(containsString("inconsistent")));
	}

	protected List<IDexTreeVisitor> getPasses() {
		return Jadx.getPassesList(new DefaultJadxArgs() {
			@Override
			public boolean isCFGOutput() {
				return outputCFG;
			}

			@Override
			public boolean isRawCFGOutput() {
				return outputCFG;
			}

			@Override
			public boolean isFallbackMode() {
				return isFallback;
			}
		}, new File(outDir));
	}

	protected MethodNode getMethod(ClassNode cls, String method) {
		for (MethodNode mth : cls.getMethods()) {
			if (mth.getName().equals(method)) {
				return mth;
			}
		}
		fail("Method not found " + method + " in class " + cls);
		return null;
	}

	void compile(ClassNode cls) {
		if (!compile) {
			return;
		}
		try {
			dynamicCompiler = new DynamicCompiler(cls);
			boolean result = dynamicCompiler.compile();
			assertTrue("Compilation failed on code: \n\n" + cls.getCode() + "\n", result);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	public Object invoke(String method) throws Exception {
		return invoke(method, new Class[0]);
	}

	public Object invoke(String method, Class[] types, Object... args) {
		Method mth = getReflectMethod(method, types);
		return invoke(mth, args);
	}

	public Method getReflectMethod(String method, Class... types) {
		assertNotNull("dynamicCompiler not ready", dynamicCompiler);
		try {
			return dynamicCompiler.getMethod(method, types);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		return null;
	}

	public Object invoke(Method mth, Object... args) {
		assertNotNull("dynamicCompiler not ready", dynamicCompiler);
		assertNotNull("unknown method", mth);
		try {
			return dynamicCompiler.invoke(mth, args);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		return null;
	}

	public File getJarForClass(Class<?> cls) throws IOException {
		String path = cls.getPackage().getName().replace('.', '/');
		List<File> list = getClassFilesWithInners(cls);

		File temp = createTempFile(".jar");
		JarOutputStream jo = new JarOutputStream(new FileOutputStream(temp));
		for (File file : list) {
			FileUtils.addFileToJar(jo, file, path + "/" + file.getName());
		}
		jo.close();
		return temp;
	}

	protected File createTempFile(String suffix) {
		File temp = null;
		try {
			temp = File.createTempFile("jadx-tmp-", System.nanoTime() + suffix);
			if (deleteTmpFiles) {
				temp.deleteOnExit();
			} else {
				System.out.println("Temporary file path: " + temp.getAbsolutePath());
			}
		} catch (IOException e) {
			fail(e.getMessage());
		}
		return temp;
	}

	private List<File> getClassFilesWithInners(Class<?> cls) {
		List<File> list = new ArrayList<File>();
		String pkgName = cls.getPackage().getName();
		URL pkgResource = ClassLoader.getSystemClassLoader().getResource(pkgName.replace('.', '/'));
		if (pkgResource != null) {
			try {
				String clsName = cls.getName();
				File directory = new File(pkgResource.toURI());
				String[] files = directory.list();
				for (String file : files) {
					String fullName = pkgName + "." + file;
					if (fullName.startsWith(clsName)) {
						list.add(new File(directory, file));
					}
				}
			} catch (URISyntaxException e) {
				fail(e.getMessage());
			}
		}
		return list;
	}

	// Try to make test class compilable
	@Deprecated
	public void disableCompilation() {
		this.compile = false;
	}

	// Use only for debug purpose
	@Deprecated
	protected void setOutputCFG() {
		this.outputCFG = true;
	}

	// Use only for debug purpose
	@Deprecated
	protected void setFallback() {
		this.isFallback = true;
	}

	// Use only for debug purpose
	@Deprecated
	protected void notDeleteTmpJar() {
		this.deleteTmpFiles = false;
	}
}
