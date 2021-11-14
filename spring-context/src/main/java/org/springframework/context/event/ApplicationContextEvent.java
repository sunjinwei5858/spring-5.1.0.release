/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.event;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

/**
 * 1、ApplicationEvent最重要的子类是ApplicationContextEvent抽象类
 * 2、该类是spring容器context生命周期事件的基类
 * 3、该类有四个子类：
 * 		1.ContextRefreshedEvent：当spring容器context刷新时触发
 * 		2.ContextStartedEvent：当spring容器context启动后触发
 * 		3.ContextStopperEvent：当spring容器context停止时触发
 * 		4.ContextClosedEvent：当spring容器context关闭时触发，容器被关闭时，其管理的所有单例bean都被销毁
 *
 * 4、以上四个事件就是spring容器生命周期的四个事件，
 * 当每个事件触发时，相关的监听器就会监听到相应事件，
 * 然后触发onApplicationEvent方法，此时就可以做一些容器，
 * 同时这些容器事件跟spring的后置处理器一样，留给用户扩展自定义逻辑，作为暴露的扩展点。
 *
 * Base class for events raised for an {@code ApplicationContext}.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
@SuppressWarnings("serial")
public abstract class ApplicationContextEvent extends ApplicationEvent {

	/**
	 * Create a new ContextStartedEvent.
	 * @param source the {@code ApplicationContext} that the event is raised for
	 * (must not be {@code null})
	 */
	public ApplicationContextEvent(ApplicationContext source) {
		super(source);
	}

	/**
	 * Get the {@code ApplicationContext} that the event was raised for.
	 */
	public final ApplicationContext getApplicationContext() {
		return (ApplicationContext) getSource();
	}

}
