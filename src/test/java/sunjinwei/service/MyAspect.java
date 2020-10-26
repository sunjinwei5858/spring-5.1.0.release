package sunjinwei.service;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Aspect
public class MyAspect {

    @Pointcut("execution(* sunjinwei.service.StudentService.getName())")
    public void myPointcut() {
    }


    @Before(value = "myPointcut()")
    public void methodBefore(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        System.out.println("执行目标方法【 " + methodName + "】前置通知， 入参为" + Arrays.asList(joinPoint.getTarget()));

    }

    @After(value = "myPointcut()")
    public void methodAfter(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        System.out.println("执行目标方法【 " + methodName + "】后置通知， 入参为" + Arrays.asList(joinPoint.getTarget()));
    }


    @AfterReturning(value = "myPointcut()", returning = "result")
    public void methodMethodReturning(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        System.out.println("执行目标方法【 " + methodName + "】返回通知， 入参为" + Arrays.asList(joinPoint.getTarget()));
    }

    @AfterThrowing(value = "myPointcut()")
    public void methodMethodAfterThrowing(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        System.out.println("执行目标方法【 " + methodName + "】异常通知， 入参为" + Arrays.asList(joinPoint.getTarget()));
    }


}
