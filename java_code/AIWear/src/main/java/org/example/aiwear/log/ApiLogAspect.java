package org.example.aiwear.log;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.aiwear.common.Result;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 日志切面
 * 在接口方法执行前后，自动插入一段公共逻辑
 */

@Slf4j
@Component
@Aspect
public class ApiLogAspect {


    //定义切点
    @Pointcut("@annotation(org.example.aiwear.log.ApiLog)")
    public void apiLogPointCut() {
    }


    //环绕通知
    // 环绕通知是 AOP 里功能最强的一种通知。
    //它可以在目标方法执行前、执行后、出现异常时都做处理。
    // ProceedingJoinPoint 类型的 joinPoint 表示当前被拦截的方法。
    // 通过 joinPoint 可以拿到：
    //方法名
    //方法参数
    //方法签名
    //也可以执行原来的方法
    @Around("apiLogPointCut()")
    public Object around(ProceedingJoinPoint joinPoint) {
        //1.获取请求信息  拿到当前这次 HTTP 请求相关的数据
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        //2.获取请求的方法
        //获取当前方法的签名信息。方法名,参数名,参数类型,返回类型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();   //拿到原来方法 的方法名

        // 3. 获取请求参数
        Object[] args = joinPoint.getArgs();  //参数对象

        //获取方法的参数名
        String[] paramNames = signature.getParameterNames();  //参数名

        Object result = null;  //定义变量，用来保存原方法执行后的返回结果。
        Throwable throwable = null;   //定义变量，用来保存原方法执行时出现的异常。

        //这里就实现了全局异常的捕获
        // 正常情况：result 是被拦截方法的返回值。
        // 异常情况：result 还是空值，方法提前返回 Result.serverError(...)。
        try {
            //继续执行原本被拦截的方法
            result = joinPoint.proceed();
        } catch (Throwable e) {
            throwable = e;
            //如果接口方法出异常，这里不会继续把异常抛出去，而是返回：
            if (e instanceof IllegalArgumentException) {
                return Result.clientError(e.getMessage());
            }
            return Result.serverError(e.getMessage());
        } finally {
            // 4. 正式记录⽇志
            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append("\n----API⽇志----\n");
            logBuilder.append("⽅法名：").append(methodName).append("\n");
            logBuilder.append("请求路径：").append(request.getRequestURI()).append("\n");
            logBuilder.append("请求⽅法：").append(request.getMethod()).append("\n");
            if (args != null && args.length > 0) {
                logBuilder.append("请求参数：\n");
                for (int i = 0; i < args.length; i++) {
                    String paramName = paramNames[i];
                    Object arg = args[i];
                    logBuilder.append(" ").append(paramName).append(": ").append(arg).append("\n");
                }
            }

            // 5. 记录响应
            if (throwable != null) {
                //出现异常
                logBuilder.append("执⾏异常\n");
                logBuilder.append("异常信息：").append(throwable.getMessage()).append("\n");
            } else {
                //没有出现异常
                logBuilder.append("执⾏成功\n");
                try {
                    logBuilder.append("响应结果：").append(result).append("\n");
                } catch (Exception e) {
                    logBuilder.append("响应结果无法序列化").append("\n");
                }
            }
            log.info(logBuilder.toString());
        }
        return result;  //返回原方法的执行结果
    }
}


