package sunjinwei.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@ComponentScan("sunjinwei.service")
@EnableAspectJAutoProxy()
//@EnableTransactionManagement
public class MainConfig {


}
