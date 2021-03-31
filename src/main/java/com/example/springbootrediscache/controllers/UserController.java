package com.example.springbootrediscache.controllers;

import com.example.springbootrediscache.models.User;
import com.example.springbootrediscache.repository.UserRepository;
import com.example.springbootrediscache.service.RedisLockService;
import com.example.springbootrediscache.service.RedisLockUtil;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

@RestController
@RequestMapping("/rest/user")
public class UserController {

	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Autowired
	private RedisLockService redisLockService;
	@Autowired
	private UserRepository userRepository;

	@Resource
	RedisLockUtil redisLockUtil;

	@Resource
	private RedissonClient redissonClient;

	// public UserController(UserRepository userRepository) {
	// userRepository = userRepository;
	// }

	@GetMapping("/all")
	public Map<String, User> GetAll() {
		return userRepository.findAll();
	}

	@GetMapping("/all/{id}")
	public User GetAll(@PathVariable("id") final String id) {
		return userRepository.findById(id);
	}

	@PostMapping("/add")
	public User add(@RequestBody User user) {
		userRepository.save(new User(user.getId(), user.getName(), 80000L));
		return userRepository.findById(user.getId());

	}

	@PostMapping("/update")
	public User update(@RequestBody User user) {
		userRepository.update(new User(user.getId(), user.getName(), 1000L));
		return userRepository.findById(user.getId());

	}

	@PostMapping("/createOrder")
	// @Transactional(rollbackFor = Exception.class)
	public boolean createOrder(String userId) {

		RLock lock = redissonClient.getLock("stock:" + userId);

		try {
			lock.lock(5, TimeUnit.SECONDS);

			Map<String, User> stock = userRepository.findAll();
			logger.info("stock :"+ stock.values().size());
			if (stock.size() <= 0) {
				return false;
			}
			userRepository.delete(userId);
			Map<String, User> afterStock = userRepository.findAll();
			logger.info("after stock :"+  afterStock.values().size());
			return true;
		} catch (Exception ex) {
			logger.error("unexpected", ex);
		} finally {
			lock.unlock();
		}

		return false;

	}

	@PostMapping("/addLock")
	public String addWithLock(@RequestParam("key") String key) {

		for (int i = 0; i < 10; i++) {
			new Thread(() -> {
				redisLockService.lock(key);
				try {
					Thread.sleep(3000L);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				logger.info(DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
				redisLockService.unlock(key);
			}).start();
		}
		return "";
	}

	@RequestMapping("/buy")
	public String buy(@RequestParam String goodId) {
		long timeout = 15;
		TimeUnit timeUnit = TimeUnit.SECONDS;
		// UUID as value
		String lockValue = UUID.randomUUID().toString();
		if (redisLockUtil.lock(goodId, lockValue, timeout, timeUnit)) {
			// Business processing
			logger.info("Obtain the lock for business processing");
			try {
				// Sleep for 10 seconds
				Thread.sleep(10 * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			// Release the lock
			if (redisLockUtil.unlock(goodId, lockValue)) {
				logger.error("redis Distributed lock unlock exception key by" + goodId);
			}
			return "Purchase successful";
		}
		return "Please try again later";
	}

	@RequestMapping("/buys")
	public String buys(@RequestParam String goodId) {
		long timeout = 4;
		TimeUnit timeUnit = TimeUnit.SECONDS;
		// UUID as value
		String lockValue = UUID.randomUUID().toString();
		for (int i = 0; i < 10; i++) {
			new Thread(() -> {
				if (redisLockUtil.lock(goodId, lockValue, timeout, timeUnit)) {
					// Business processing
					logger.info("Obtain the lock for business processing");
					try {
						// Sleep for 3 seconds
						Thread.sleep(3000L);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					// Release the lock
					if (redisLockUtil.unlock(goodId, lockValue)) {
						logger.error("redis Distributed lock unlock exception key by" + goodId);
					}
				}
			}).start();
		}
		return "";
	}
}
