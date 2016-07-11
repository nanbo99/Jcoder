package org.nlpcn.jcoder.run.mvc.cache;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.nlpcn.jcoder.domain.Task;
import org.nlpcn.jcoder.run.java.JavaRunner;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * api请求实体
 * 
 * @author ansj
 * 
 */
public class CacheEntry {

	private static final Logger LOG = Logger.getLogger(CacheEntry.class);

	public static final Object NULL = new Object();

	private Method method;

	// 缓存时间
	private int time = 10;

	// cachesize
	private int size = 1000;

	// 执行的task
	private Task task;

	// 是否责塞
	private boolean block = true;

	private LoadingCache<Args, Object> cache = null;

	public CacheEntry(Task task, Method method, int time, int size, boolean block) {
		this.task = task;

		if (size > 0) {
			this.size = size;
		}

		this.block = block;

		this.method = method;

		this.time = time;

		if (time <= 0) {
			this.time = Integer.MAX_VALUE;
		}

		if (this.block) {
			cache = CacheBuilder.newBuilder().maximumSize(this.size).softValues().refreshAfterWrite(time, TimeUnit.SECONDS).build(new CacheLoader<Args, Object>() {
				@Override
				public Object load(Args args) {
					return executeNoCache(args);
				}
			});

		} else {
			cache = CacheBuilder.newBuilder().maximumSize(this.size).refreshAfterWrite(time, TimeUnit.SECONDS).build(new CacheLoader<Args, Object>() {

				public Object load(Args args) {
					return executeNoCache(args);
				}

				private ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

				@Override
				public ListenableFuture<Object> reload(final Args args, Object oldValue) {
					ListenableFuture<Object> result = ListenableFutureTask.create(new Callable<Object>() {
						public Object call() {
							return executeNoCache(args);
						}
					});

					result = executorService.submit(new Callable<Object>() {
						@Override
						public Object call() {
							return executeNoCache(args);
						}
					});
					return result;
				}

			});
		}

	}

	public Object execute(Object[] param) throws ExecutionException {

		Args args = Args.create(param);

		long start = System.currentTimeMillis();
		try {
			return cache.get(args);
		} finally {
			LOG.info("by cache action " + this.task.getName() + "/" + this.method.getName() + " Param:" + args + " ok ! use time : " + (System.currentTimeMillis() - start));
		}
	}

	private Object executeNoCache(Args args) {
		Object result = new JavaRunner(task).compile().instance().execute(method, args.getArgs());
		return result == null ? NULL : result;
	}

}
