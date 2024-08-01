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

import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import datadog.trace.api.Trace;
import datadog.trace.api.DDTags;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

import io.opentracing.util.GlobalTracer;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 */
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

		Tracer tracer = GlobalTracer.get();
		Span span = tracer.buildSpan("GET /owners")
			.withTag(DDTags.SERVICE_NAME, "petclinic-dd-manual")
			.withTag(DDTags.RESOURCE_NAME, "GET /owners")
			.start();

		// allow parameterless GET request for /owners to return all records
		if (owner.getLastName() == null) {
			owner.setLastName(""); // empty string signifies broadest possible search
		}

		Page<Owner> ownersResults = findPaginatedForOwnersLastName(page, owner.getLastName());

		try (Scope scope = tracer.activateSpan(span)) {
			// allow parameterless GET request for /owners to return all records
			if (owner.getLastName() == null) {
				owner.setLastName(""); // empty string signifies broadest possible search
			}
			// find owners by last name

			// Scope scope_num = tracer.buildSpan("Owner found").startActive(true);
			// scope_num.span().setTag("number", ownersResults.getTotalElements());
			Span span_num = tracer.buildSpan("Number of owners").start();
			Scope scope_num = tracer.activateSpan(span_num);
			span_num.setTag("Number of owners", ownersResults.getTotalElements());
			span_num.finish();
			scope_num.close();

			if (ownersResults.isEmpty()) {
				// no owners found
				result.rejectValue("lastName", "notFound", "not found");
				return "owners/findOwners";
			}

			if (ownersResults.getTotalElements() == 1) {
				// 1 owner found
				owner = ownersResults.iterator().next();
				// Scope scope_owner = tracer.buildSpan("Owner found").startActive(true);
				// scope_owner.span().setTag("ID", owner.getId());
				// scope_owner.close();
				Span span_owner = tracer.buildSpan("Owner").start();
				Scope scope_owner = tracer.activateSpan(span_owner);
				span_owner.setTag("Owner id", owner.getId());
				span_owner.finish();
				scope_owner.close();
				return "redirect:/owners/" + owner.getId();
			}
			// Iterator<Owner> ownersIterator = ownersResults.iterator();
			ownersResults.forEach(individualOwner -> {
				// Process each Owner object
				Span span_owner = tracer.buildSpan("Owner").start();
				Scope scope_owner = tracer.activateSpan(span_owner);
				span_owner.setTag("Owner id", individualOwner.getId());
				span_owner.finish();
				scope_owner.close();
			});
			// while (ownersIterator.hasNext()) {
			// owner = ownersIterator.next();
			// // Scope scope_owner = tracer.buildSpan("Owner found").startActive(true);
			// // scope_owner.span().setTag("ID", owner.getId());
			// // scope_owner.close();
			// }

			// StringJoiner idsJoiner = new StringJoiner(",");

			// while (ownersIterator.hasNext()) {
			// owner = ownersIterator.next();
			// idsJoiner.add(String.valueOf(owner.getId()));
			// }
			// StringJoiner idsJoiner = new StringJoiner(",");
			// Iterator<Owner> ownersIterator = ownersResults.iterator();
			// while (ownersIterator.hasNext()) {
			// owner = ownersIterator.next();
			// idsJoiner.add(String.valueOf(owner.getId()));
			// }

			// span.setTag("OwnerIDs", idsJoiner.toString());
			span_num.finish();
			scope_num.close();

		}
		catch (Exception e) {
			// Set error on span
			span.setTag(Tags.ERROR, true);
			span.setTag(DDTags.ERROR_MSG, e.getMessage());
			span.setTag(DDTags.ERROR_TYPE, e.getClass().getName());

			final StringWriter errorString = new StringWriter();
			e.printStackTrace(new PrintWriter(errorString));
			span.setTag(DDTags.ERROR_STACK, errorString.toString());
		}
		finally {
			span.finish();
			return addPaginationModel(page, model, ownersResults);
		}
	}

	String addPaginationModel(int page, Model model, Page<Owner> paginated) {
		List<Owner> listOwners = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listOwners", listOwners);
		return "owners/ownersList";
	}

	Page<Owner> findPaginatedForOwnersLastName(int page, String lastname) {
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
