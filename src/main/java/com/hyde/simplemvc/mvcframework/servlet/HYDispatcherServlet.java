package com.hyde.simplemvc.mvcframework.servlet;

import com.hyde.simplemvc.mvcframework.annotation.HYAutowired;
import com.hyde.simplemvc.mvcframework.annotation.HYController;
import com.hyde.simplemvc.mvcframework.annotation.HYRequestMapping;
import com.hyde.simplemvc.mvcframework.annotation.HYService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class HYDispatcherServlet extends HttpServlet {

    private static final long serialVersionUID = -3682638788402241339L;
    // 与web.xml 中param-name 的值 一样
    private static final String LOCATION = "contextConfigLocation";
    // 配置信息
    private Properties p = new Properties();
    // 保持所有被扫描到的相关的类名
    private List<String> className = new ArrayList<String>();
    // 核心IOC 容器, 保存所有初始化的bean
    private Map<String, Object> ioc = new HashMap<String, Object>();
    // 保存所有的url 和方法的映射关系
    private Map<String, Method> handleMapping = new HashMap<String, Method>();
    public HYDispatcherServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try{
            doDispatch(req, resp); // 开始匹配对应的方法.
        }catch (Exception e) {
            resp.getWriter().write("500 Exception, Details" + e.getMessage());
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if(this.handleMapping.isEmpty()) {return;}
        String url = req.getRequestURI();
        System.out.println("doDispatch url:" + url);
        String contextPath = req.getContextPath();
        System.out.println("doDispatch contextPath: " + contextPath);
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        System.out.println("doDispatch 请求url: " +url);
        System.out.println("url 映射为: map-" + this.handleMapping);
        if(!this.handleMapping.containsKey(url)) {
            resp.getWriter().write("404 Not found!!");
            return;
        }
        Method method = this.handleMapping.get(url);
        // 获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 获取请求参数
        Map<String, String[]> parameterMap = req.getParameterMap();
        // 保存参数值
        Object[] paramValues = new Object[parameterTypes.length];
        // 方法的参数列表
        for (int i = 0; i < parameterTypes.length ; i++) {
            // 根据参数名称， 做某些处理
            Class parameterType = parameterTypes[i];
            if(parameterType == HttpServletRequest.class) {
                // 参数类型已经明确， 强转类型
                paramValues[i] = req;
                continue;
            } else if(parameterType == HttpServletResponse.class) {
                paramValues[i] = resp;
            } else if(parameterType == String.class) {
                for (Map.Entry<String, String[]> param : parameterMap.entrySet()) {
                    String value = Arrays.toString(param.getValue())
                            .replaceAll("\\[|\\]", "")
                            .replaceAll(",\\s", ",");
                    paramValues[i] = value;
                }
            }
        }
        try {
            String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
            // 方法反射调用
            System.out.println(String.format("beanName:%s, paramValues:%s", beanName, paramValues));
            System.out.println(String.format("ioc %s", ioc));
            Object result = method.invoke(this.ioc.get(beanName), paramValues);
            resp.getWriter().write(result.toString());
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1. 加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        //2. 扫描所有相关的类
        doScanner(p.getProperty("scanPackage"));
        // 3. 初始化所有相关的类的实例, 并保存到IOC 容器中.
        doInstance();
        // 4. 依赖注入
        doAutowired();
        // 5. 构造HandleMapping
        initHandleMapping();
        //6. 等待请求, 匹配URL, 定位方法, 反射调用执行
        // 调用doGet 或者doPost 方法

        // 提示信息
        System.out.println("Hyde mvcframework is init successful");
    }

    /**
     * @description: 加载配置文件.
     * @param location
     * @return void
     * @date 2018/12/27 18:11
     */
    private void doLoadConfig(String location) {
        InputStream fis = null;
        try{
            System.out.println("1, doLoadConfig: location--" + location);
            fis = this.getClass().getClassLoader().getResourceAsStream(location);
            System.out.println("2, doLoadConfig......###" + fis);
            p.load(fis);
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            try {
                if (null != fis) {
                    fis.close();
                }
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @description: 扫描包.
     * @param packageName
     * @return void
     * @date 2018/12/28 12:07
     */
    private void doScanner(String packageName) {
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        System.out.println("doScanner- url: " + url);
        File dir = new File(url.getFile());
        for(File file: dir.listFiles()) {
            System.out.println("doScanner-- file name:" + file.getName());
            if(file.isDirectory()) {
                doScanner(packageName + "." + file.getName());
            }else{
                className.add(packageName + "." + file.getName().replace(".class", "").trim());
            }
        }
    }

    /**
     * @description: 初始化所有相关的类，并放入到IOC容器之中。IOC容器的key默认是类名首字母小写，
     *  如果是自己设置类名，则优先使用自定义的
     * @param
     * @return void
     * @date 2018/12/28 12:06
     */
    private void doInstance() {
        if(className.size() == 0) { return;}
        try{
            for(String item: className) {
                Class<?> clazz = Class.forName(item);
                if(clazz.isAnnotationPresent(HYController.class)) {
                    // 首字母小写
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                }else if(clazz.isAnnotationPresent(HYService.class)) {
                    HYService service = clazz.getAnnotation(HYService.class);
                    String beanName = service.value();
                    // 如果用户设置了名字， 使用用户的设置
                    if(!"".equals(beanName.trim())){
                        ioc.put(beanName, clazz.newInstance());
                        continue;
                    }
                    // 如果自己没有设置, autowired 的是接口， 通过接口获取beanName..
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for(Class<?> i: interfaces) {
                        ioc.put(i.getName(), clazz.newInstance());
                    }
                }else {
                    continue;
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @description: 初始化IOC 容器中的类， 需要赋值的字段进行赋值
     * @param
     * @return void
     * @date 2018/12/28 12:06
     */
    private void doAutowired() {
        if(ioc.isEmpty()) {return;}
        for (Map.Entry<String, Object> stringObjectEntry : ioc.entrySet()) {
            Field[] fields = stringObjectEntry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if(!field.isAnnotationPresent(HYAutowired.class)){continue;}
                HYAutowired autowired = field.getAnnotation(HYAutowired.class);
                String beanName = autowired.value().trim();
                if("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                field.setAccessible(true); // 设置私有属性的访问权限.
                try{
                    // 修改autowired field 的值.
                    field.set(stringObjectEntry.getValue(), ioc.get(beanName));
                }catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    /**
     * @description: initHandlerMapping()方法，将HYRequestMapping中配置的信息和Method进行关联，并保存这些关系
     * @param
     * @return void
     * @date 2018/12/28 12:06
     */
    private void initHandleMapping() {
        if(ioc.isEmpty()) {return;}
        for (Map.Entry<String, Object> stringObjectEntry : ioc.entrySet()) {
            Class<?> clazz = stringObjectEntry.getValue().getClass();
            if(!clazz.isAnnotationPresent(HYController.class)) {continue;}

            String baseUrl = "";
            // 获取Controller 的url 配置
            if(clazz.isAnnotationPresent(HYRequestMapping.class)){
                HYRequestMapping requestMapping = clazz.getAnnotation(HYRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            // 获取Method 的url 的配置
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                // 没有添加RequestMapping 的注解信息直接忽略
                if(!method.isAnnotationPresent(HYRequestMapping.class)) {continue;}

                // 映射URL
                HYRequestMapping requestMapping = method.getAnnotation(HYRequestMapping.class);
                String resultUrl = "/" + baseUrl + "/" +requestMapping.value();
                System.out.println("url:#########" +  resultUrl);
                String url = resultUrl.replaceAll("/+", "/");
                handleMapping.put(url, method);
                System.out.println("mapped " + url + "," +method);
            }

        }

    }

    private String lowerFirstCase(String str) {
        char [] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }
}
