package com.hyde.simplemvc.demo.service.impl;

import com.hyde.simplemvc.demo.service.IDemoService;
import com.hyde.simplemvc.mvcframework.annotation.HYService;

@HYService
public class DemoService implements IDemoService {
    public String get(String name) {
        return "Hello name:" + name;
    }

    public void sayHello() {

    }
}
