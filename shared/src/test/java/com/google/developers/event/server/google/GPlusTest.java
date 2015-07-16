package com.google.developers.event.server.google;

import com.google.developers.event.server.DevelopersSharedModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by frren on 2015-07-10.
 */
public class GPlusTest {

	private final Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
			new DevelopersSharedModule());

	@Test
	public void test() throws IOException {
		injector.getInstance(GPlusManager.class).x();
	}
}
