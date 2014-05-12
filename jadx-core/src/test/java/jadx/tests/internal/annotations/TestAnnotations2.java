package jadx.tests.internal.annotations;

import jadx.api.InternalJadxTest;
import jadx.core.dex.nodes.ClassNode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class TestAnnotations2 extends InternalJadxTest {

	public static class TestCls {

		@Target({ElementType.TYPE})
		@Retention(RetentionPolicy.RUNTIME)
		public static @interface A {
			int i();

			float f();
		}
	}

	@Test
	public void test() {
		ClassNode cls = getClassNode(TestCls.class);
		String code = cls.getCode().toString();
		System.out.println(code);

		assertThat(code, containsString("@Target({ElementType.TYPE})"));
		assertThat(code, containsString("@Retention(RetentionPolicy.RUNTIME)"));
		assertThat(code, containsString("public static @interface A {"));
		assertThat(code, containsString("float f();"));
		assertThat(code, containsString("int i();"));
	}
}
