package jadx.api;

import jadx.core.dex.nodes.ClassNode;

import java.util.List;

public class Factory {

	public static JavaPackage newPackage(String name, List<JavaClass> classes) {
		return new JavaPackage(name, classes);
	}

	public static JavaClass newClass(Decompiler decompiler, ClassNode classNode) {
		return new JavaClass(decompiler, classNode);
	}
}
