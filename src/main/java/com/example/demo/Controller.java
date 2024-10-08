package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
	
	@Value("${app.env}")
	private String env;

	@GetMapping("/hello")
	public String hello() {
		return "hello ["+env +"]";
	}

}
