package sunjinwei.mvc;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;


/**
 * 简单看一下ServletContextListener的用法，还需要自己配置web.xml，将自己重写的监听器MyDataContextListener 配置一下
 * <listener>
 *     sunjinwei.mvc.MyDataContextListener
 * </listener>
 */
public class MyDataContextListener implements ServletContextListener {

    private ServletContext servletContext;


    /**
     * 该方法在ServletContext启动后被调用 并准备好处理客户端请求
     *
     * @param sce
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        this.servletContext = sce.getServletContext();
        servletContext.setAttribute("myData", "this is myData");

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        this.servletContext = null;

    }
}
