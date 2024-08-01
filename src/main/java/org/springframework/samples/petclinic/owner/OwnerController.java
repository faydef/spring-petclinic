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
package org.springframework.samples.petclinic.owner;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;

import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 */

// SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
// .addSpanProcessor(spanProcessor)
// .setResource(resource)
// .build();

@Controller
class OwnerController {

	private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";

	private final OwnerRepository owners;

	public OwnerController(OwnerRepository clinicService) {
		this.owners = clinicService;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}

	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable(name = "ownerId", required = false) Integer ownerId) {
		return ownerId == null ? new Owner() : this.owners.findById(ownerId);
	}

	@GetMapping("/owners/new")
	public String initCreationForm(Map<String, Object> model) {
		Owner owner = new Owner();
		model.put("owner", owner);
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/new")
	public String processCreationForm(@Valid Owner owner, BindingResult result, RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in creating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Owner Created");
		return "redirect:/owners/" + owner.getId();
	}

	@GetMapping("/owners/find")
	public String initFindForm() {
		return "owners/findOwners";
	}

	@GetMapping("/owners")
	public String processFindForm(@RequestParam(defaultValue = "1") int page, Owner owner, BindingResult result,
			Model model) {
		String SERVICE_NAME = "otel-manual-java";
		Span span = GlobalOpenTelemetry.getTracer(SERVICE_NAME).spanBuilder("GET /owners").startSpan();

		// allow parameterless GET request for /owners to return all records
		if (owner.getLastName() == null) {
			owner.setLastName(""); // empty string signifies broadest possible search
		}

		Page<Owner> ownersResults = findPaginatedForOwnersLastName(page, owner.getLastName());

		try (Scope scope = span.makeCurrent();) {
			// allow parameterless GET request for /owners to return all records
			if (owner.getLastName() == null) {
				owner.setLastName(""); // empty string signifies broadest possible search
			}
			// find owners by last name
			Span span_num = GlobalOpenTelemetry.getTracer(SERVICE_NAME).spanBuilder("Number of owners").startSpan();
			Scope scope_num = span_num.makeCurrent();
			span_num.setAttribute("Number of owners", ownersResults.getTotalElements());
			span_num.end();
			scope_num.close();

			if (ownersResults.isEmpty()) {
				// no owners found
				result.rejectValue("lastName", "notFound", "not found");
				return "owners/findOwners";
			}

			if (ownersResults.getTotalElements() == 1) {
				// 1 owner found
				owner = ownersResults.iterator().next();
				Span span_owner = GlobalOpenTelemetry.getTracer(SERVICE_NAME)
					.spanBuilder("Number of owners")
					.startSpan();
				Scope scope_owner = span_owner.makeCurrent();
				span_owner.setAttribute("Owner id", owner.getId());
				span_owner.end();
				scope_owner.close();
				return "redirect:/owners/" + owner.getId();
			}

			ownersResults.forEach(individualOwner -> {
				// Process each Owner object
				Span span_owner = GlobalOpenTelemetry.getTracer(SERVICE_NAME)
					.spanBuilder("Number of owners")
					.startSpan();
				Scope scope_owner = span_owner.makeCurrent();
				span_owner.setAttribute("Owner id", individualOwner.getId());
				span_owner.end();
				scope_owner.close();
			});

			span_num.end();
			scope_num.close();

		}
		catch (Exception e) {
			// Set error on span
			span.setAttribute("tags.error", true);
			span.setAttribute("exception.error_msg", e.getMessage());
			span.setAttribute("exception.error_type", e.getClass().getName());

			final StringWriter errorString = new StringWriter();
			e.printStackTrace(new PrintWriter(errorString));
			span.setAttribute("exception.error_stack", errorString.toString());
		}
		finally {
			span.end();
			return addPaginationModel(page, model, ownersResults);
		}
	}

	private String addPaginationModel(int page, Model model, Page<Owner> paginated) {
		List<Owner> listOwners = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listOwners", listOwners);
		return "owners/ownersList";
	}

	private Page<Owner> findPaginatedForOwnersLastName(int page, String lastname) {
		int pageSize = 5;
		Pageable pageable = PageRequest.of(page - 1, pageSize);
		return owners.findByLastName(lastname, pageable);
	}

	@GetMapping("/owners/{ownerId}/edit")
	public String initUpdateOwnerForm(@PathVariable("ownerId") int ownerId, Model model) {
		Owner owner = this.owners.findById(ownerId);
		model.addAttribute(owner);
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/{ownerId}/edit")
	public String processUpdateOwnerForm(@Valid Owner owner, BindingResult result, @PathVariable("ownerId") int ownerId,
			RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in updating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		owner.setId(ownerId);
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "Owner Values Updated");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Custom handler for displaying an owner.
	 * @param ownerId the ID of the owner to display
	 * @return a ModelMap with the model attributes for the view
	 */
	@GetMapping("/owners/{ownerId}")
	public ModelAndView showOwner(@PathVariable("ownerId") int ownerId) {
		ModelAndView mav = new ModelAndView("owners/ownerDetails");
		Owner owner = this.owners.findById(ownerId);
		mav.addObject(owner);
		return mav;
	}

}
