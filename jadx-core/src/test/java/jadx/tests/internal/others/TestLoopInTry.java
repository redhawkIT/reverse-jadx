package jadx.tests.internal.others;

import jadx.api.InternalJadxTest;
import jadx.core.dex.nodes.ClassNode;

import org.junit.Test;

import static jadx.tests.utils.JadxMatchers.containsOne;
import static org.junit.Assert.assertThat;

public class TestLoopInTry extends InternalJadxTest {

	public static class TestCls {
		private static boolean b = true;

		public int test() {
			try {
				if (b) {
					throw new Exception();
				}
				while (f()) {
					s();
				}
			} catch (Exception e) {
				System.out.println("exception");
				return 1;
			}
			return 0;
		}

		private static void s() {
		}

		private static boolean f() {
			return false;
		}
	}

	@Test
	public void test() {
		ClassNode cls = getClassNode(TestCls.class);
		String code = cls.getCode().toString();
		System.out.println(code);

		assertThat(code, containsOne("try {"));
		assertThat(code, containsOne("if (b) {"));
		assertThat(code, containsOne("throw new Exception();"));
		assertThat(code, containsOne("while (f()) {"));
		assertThat(code, containsOne("s();"));
		assertThat(code, containsOne("} catch (Exception e) {"));
		assertThat(code, containsOne("return 1;"));
		assertThat(code, containsOne("return 0;"));
	}
}
