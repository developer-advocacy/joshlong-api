package com.joshlong.blog.utils;

import lombok.extern.log4j.Log4j2;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Log4j2
public abstract class ThreadLocalUtils {

	private static final Map<String, ThreadLocal<Object>> threadLocalMap = new ConcurrentHashMap<>();

	public static <T> T buildThreadLocalObject(String beanName, Class<T> clazz, Supplier<T> supplier) {

		var pfb = new ProxyFactory();
		for (var i : clazz.getInterfaces()) {
			pfb.addInterface(i);
		}
		pfb.setTarget(supplier.get());
		pfb.setTargetClass(clazz);
		pfb.setProxyTargetClass(true);
		pfb.addAdvice((MethodInterceptor) invocation -> {
			log.debug("invoking " + invocation.getMethod().getName() + " for beanName " + beanName + " on thread "
					+ Thread.currentThread().getName() + '.');
			var tl = threadLocalMap.computeIfAbsent(beanName, s -> new ThreadLocal<>());
			if (tl.get() == null) {
				log.debug("There is no bean instance of type " + clazz.getName() + " " + "on the thread "
						+ Thread.currentThread().getName() + ". " + "Constructing an instance by calling the supplier");
				tl.set(supplier.get());
			}
			log.debug(
					"fetching an instance of " + clazz.getName() + " for the thread " + Thread.currentThread().getName()
							+ " and there are " + threadLocalMap.size() + " thread local(s)");
			var obj = tl.get();
			var method = invocation.getMethod();
			return method.invoke(obj, invocation.getArguments());
		});

		return (T) pfb.getProxy();
	}

}
