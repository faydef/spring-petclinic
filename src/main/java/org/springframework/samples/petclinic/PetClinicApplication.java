/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
// import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

import java.util.Locale;

/**
 * PetClinic Spring Boot Application.
 *
 * @author Dave Syer
 *
 */
@SpringBootApplication
@ImportRuntimeHints(PetClinicRuntimeHints.class)
public class PetClinicApplication {

	public static void main(String[] args) {
		String SERVICE_NAME = "otel-manual-java";

		// set service name on all OTel signals
		// Resource resource = Resource.getDefault().merge(Resource.create(
		// Attributes.of(ResourceAttributes.SERVICE_NAME,
		// SERVICE_NAME,ResourceAttributes.SERVICE_VERSION,"1.0",ResourceAttributes.DEPLOYMENT_ENVIRONMENT,"production")));
		Resource resource = Resource.getDefault();

		// init OTel logger provider with export to OTLP
		// SdkLoggerProvider sdkLoggerProvider = SdkLoggerProvider.builder()
		// .setResource(resource)
		// .addLogRecordProcessor(BatchLogRecordProcessor.builder(
		// OtlpGrpcLogRecordExporter.builder().setEndpoint(
		// System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")).addHeader("Authorization",
		// "Bearer " + System.getenv("ELASTIC_APM_SECRET_TOKEN"))

		// .build())
		// .build())
		// .build();

		// init OTel trace provider with export to OTLP
		SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
			.setResource(resource)
			.setSampler(Sampler.alwaysOn())
			// add span processor to add baggage as span attributes
			.addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder()
				// .setEndpoint(System.getenv(
				// "OTEL_EXPORTER_OTLP_ENDPOINT"))
				.build()).build())
			.build();

		// init OTel meter provider with export to OTLP
		// SdkMeterProvider sdkMeterProvider =
		// SdkMeterProvider.builder().setResource(resource)
		// .registerMetricReader(PeriodicMetricReader.builder(
		// OtlpGrpcMetricExporter.builder().setEndpoint(System
		// .getenv("OTEL_EXPORTER_OTLP_ENDPOINT")).addHeader("Authorization", "Bearer " +
		// System.getenv("ELASTIC_APM_SECRET_TOKEN"))
		// .build())
		// .build())
		// .build();

		// create sdk object and set it as global
		OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
			.setTracerProvider(sdkTracerProvider)
			// .setLoggerProvider(sdkLoggerProvider)
			// .setMeterProvider(sdkMeterProvider)
			.setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
			.build();
		GlobalOpenTelemetry.set(sdk);

		SpringApplication.run(PetClinicApplication.class, args);
	}

}
