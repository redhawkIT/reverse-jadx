package jadx.tests.integration.arith;

import jadx.tests.api.IntegrationTest;
import jadx.core.dex.nodes.ClassNode;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class TestFieldIncrement2 extends IntegrationTest {

	public static class TestCls {
		private static class A {
			int f = 5;
		}

		public A a;

		public void test1(int n) {
			this.a.f = this.a.f + n;
		}

		public void test2(int n) {
			this.a.f *= n;
		}
	}

	@Test
	public void test() {
		ClassNode cls = getClassNode(TestCls.class);
		String code = cls.getCode().toString();
		System.out.println(code);

		assertThat(code, containsString("this.a.f += n;"));
		assertThat(code, containsString("a.f *= n;"));
		// TODO
		// assertThat(code, containsString("this.a.f *= n;"));
	}
}
