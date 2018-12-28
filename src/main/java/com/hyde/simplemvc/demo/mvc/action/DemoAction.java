package com.hyde.simplemvc.demo.mvc.action;

import com.hyde.simplemvc.demo.service.IDemoService;
import com.hyde.simplemvc.mvcframework.annotation.HYAutowired;
import com.hyde.simplemvc.mvcframework.annotation.HYController;
import com.hyde.simplemvc.mvcframework.annotation.HYRequestMapping;
import com.hyde.simplemvc.mvcframework.annotation.HYRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@HYController
@HYRequestMapping("/demo")
public class DemoAction {
    @HYAutowired private IDemoService iDemoService;

    @HYRequestMapping("query.json")
    public String query(HttpServletRequest req, HttpServletResponse resp,
                      @HYRequestParam("name") String name) {
        String result = iDemoService.get(name);
        System.out.println("query:" +result);
        return result;
    }

    @HYRequestMapping("/add.json")
    public void add(HttpServletRequest req, HttpServletResponse resp,
                    @HYRequestParam("a") Integer a, @HYRequestParam("b") Integer b) {
        try {
            resp.getWriter().write(a + "+" + b + "=" + (a + b));
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    @HYRequestMapping("/remove.json")
    public void remove(HttpServletRequest req, HttpServletResponse resp,
                       @HYRequestParam("id") Integer id) {
        System.out.println("id:" +id);
    }
}
