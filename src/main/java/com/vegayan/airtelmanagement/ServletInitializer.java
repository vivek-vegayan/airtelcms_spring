package com.vegayan.airtelmanagement;


import io.micrometer.common.lang.NonNull;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

public class ServletInitializer extends SpringBootServletInitializer {

	@Override
	protected   @NonNull SpringApplicationBuilder configure(
			@NonNull SpringApplicationBuilder application) {
		return application.sources(AirtelmanagementApplication.class);
	}

}
