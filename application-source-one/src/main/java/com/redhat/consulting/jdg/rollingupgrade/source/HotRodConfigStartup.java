package com.redhat.consulting.jdg.rollingupgrade.source;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ClusteringConfiguration;
import org.infinispan.configuration.cache.ClusteringConfigurationBuilder;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.server.hotrod.configuration.HotRodServerConfiguration;
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.transaction.lookup.GenericTransactionManagerLookup;
import org.infinispan.util.concurrent.IsolationLevel;

import com.redhat.consulting.jdg.rollingupgrade.model.CustomObject;


@Singleton
@Startup
public class HotRodConfigStartup {

private static final Logger LOGGER = Logger.getLogger(HotRodConfigStartup.class.getName());
	
	
	private EmbeddedCacheManager manager;
	
	private
	HotRodServer server = null;
	
	@PostConstruct
	/**
	 * This method is called at application startup. It purpose is:
	 * 1) Initilize the Cache Manager,
	 * 2) Enable the compatibility mode,
	 * 3) Expose the HotRod server.
	 */
	public void init()
	{
		
//		System.setProperty("infinispan.deserialization.whitelist.regexps", ".*");
//		manager = new DefaultCacheManager();
//		Configuration builder = new ConfigurationBuilder()
//				.compatibility().enable()
//				//.versioning().enable().scheme(VersioningScheme.SIMPLE)	
//	           .build();
		
		System.setProperty("infinispan.deserialization.whitelist.regexps", ".*");

	      ConfigurationBuilder builder = new ConfigurationBuilder();
	      builder.clustering().cacheMode(CacheMode.DIST_SYNC);
	      builder.compatibility().enable();
	      
	      //TODO: Transactional test
	       
	      TransactionMode txMode = TransactionMode.TRANSACTIONAL;
	      GenericTransactionManagerLookup txMan = new GenericTransactionManagerLookup();
	      builder.transaction()
		      .completedTxTimeout(600000) //10 minutes
		      //.syncCommitPhase(true)
		      .useSynchronization(true)
		      .lockingMode(LockingMode.PESSIMISTIC)
		      .transactionMode(txMode)
		      .autoCommit(false)
		      .transactionManagerLookup(txMan)
		      .recovery()
		      .disable()
		      .persistence()
		      .passivation(false)
		      .locking()
		      .isolationLevel(IsolationLevel.NONE)
		      .useLockStriping(false)
		      .lockAcquisitionTimeout(30000, TimeUnit.MILLISECONDS)
		      .expiration();

	      GlobalConfigurationBuilder gcb = GlobalConfigurationBuilder.defaultClusteredBuilder();
	      gcb.globalJmxStatistics().jmxDomain("application-source-one");
	      GlobalConfiguration globalConfiguration = gcb.build();
	      DefaultCacheManager manager = new DefaultCacheManager(globalConfiguration, new ConfigurationBuilder().build(), true);

	      Configuration config = builder.build();
		
		manager.defineConfiguration("firstToMigrate", config);
		manager.defineConfiguration("secondToMigrate", config);
		manager.defineConfiguration("thirdToMigrate", config);
		manager.defineConfiguration("cache", config);
		
		LOGGER.info("INITIALIZE CACHES TO MIGRATE");
		Cache<Integer, CustomObject> cache = manager.getCache("cache");
		Cache<Integer, CustomObject> firstToMigrate = manager.getCache("firstToMigrate");
		Cache<Integer, CustomObject> secondToMigrate = manager.getCache("secondToMigrate");
		//Cache<Integer, CustomObject> thirdToMigrate = manager.getCache("thirdToMigrate");
	      if (cache.isEmpty()) {
	         for (int i = 0; i < 100; i++) {
	            cache.put(i, new CustomObject("text_cache_" +i, i));
	            firstToMigrate.put(i, new CustomObject("text_firstToMigrate_" +i, i));
	            secondToMigrate.put(i, new CustomObject("text_secondToMigrate_" +i, i));
	            //thirdToMigrate.put(i, new CustomObject("text_thirdToMigrate_" +i, i));
	         }
	      }
		
//		Cache<String,String> cache = manager.getCache("cache");
//		for (int i = 0; i < 10; i++) {
//			cache.put("k" + i, "v" + i);
//         }
//		
//		Cache<String,String> firstToMigrate = manager.getCache("firstToMigrate");
//		for (int i = 0; i < 10; i++) {
//			firstToMigrate.put("k" + i, "v" + i);
//         }
//		
//		Cache<String,String> secondToMigrate = manager.getCache("secondToMigrate");
//		for (int i = 0; i < 20; i++) {
//			secondToMigrate.put("k" + i, "v" + i);
//         }
//		
//		Cache<String,String> thirdToMigrate = manager.getCache("thirdToMigrate");
//		for (int i = 0; i < 40; i++) {
//			thirdToMigrate.put("k" + i, "v" + i);
//         }
			
				
		LOGGER.info("********************** START EXPOSING HOT ROD***********************");
		Set<String> cachesToIgnore = new HashSet<>();
		cachesToIgnore.add("cache1");
		cachesToIgnore.add("cache2");
		HotRodServerConfiguration hotRodServerConfiguration = new HotRodServerConfigurationBuilder()
	            .host(MigrationConfig.HOTROD_BIND_ADRESSS)
	            .port(MigrationConfig.HOTROD_LISTEN_PORT).ignoredCaches(cachesToIgnore)
	            .build();
	      server = new HotRodServer();
	      server.start(hotRodServerConfiguration, manager);
	      LOGGER.log(Level.INFO, String.format("Listening on hotrod://{%s}:{%s}", hotRodServerConfiguration.host(), hotRodServerConfiguration.port()));
	      
	     
	}
	
	
	
	@PreDestroy
	public void cleanup()
	{
		server.stop();
        manager.stop();
		
	}
}
