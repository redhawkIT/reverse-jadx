package jadx.api;

import java.util.List;

public final class JavaPackage implements Comparable<JavaPackage> {
	private final String name;
	private final List<JavaClass> classes;

	JavaPackage(String name, List<JavaClass> classes) {
		this.name = name;
		this.classes = classes;
	}

	public String getName() {
		return name;
	}

	public List<JavaClass> getClasses() {
		return classes;
	}

	@Override
	public int compareTo(JavaPackage o) {
		return name.compareTo(o.name);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		JavaPackage that = (JavaPackage) o;
		return name.equals(that.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return name;
	}
}
