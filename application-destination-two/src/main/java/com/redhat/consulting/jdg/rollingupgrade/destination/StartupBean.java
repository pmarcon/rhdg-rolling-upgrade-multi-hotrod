package com.redhat.consulting.jdg.rollingupgrade.destination;

import static org.infinispan.client.hotrod.ProtocolVersion.PROTOCOL_VERSION_26;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import org.infinispan.Cache;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.jboss.marshalling.commons.GenericJBossMarshaller;
import org.infinispan.jboss.marshalling.core.JBossUserMarshaller;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.persistence.remote.configuration.RemoteStoreConfigurationBuilder;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.transaction.lookup.GenericTransactionManagerLookup;
import org.infinispan.upgrade.RollingUpgradeManager;
import org.infinispan.util.concurrent.IsolationLevel;

import com.redhat.consulting.jdg.rollingupgrade.model.CustomObject;

@Singleton
@Startup
public class StartupBean {
	
	private static final Logger LOGGER = Logger.getLogger(StartupBean.class.getName());
	
//	private EmbeddedCacheManager manager;
	
	public static final String CACHE_NAME = "thirdToMigrate";
	
	@PostConstruct
	public void init()
	{
//		LOGGER.info("********************** START DESTINATION APPLICATION ***********************");
//		for(String cacheName : manager.getCacheNames()) {
//			LOGGER.info(String.format("Cache %s found", cacheName));
//		}
		
		System.setProperty("infinispan.deserialization.whitelist.regexps", ".*");
	      ConfigurationBuilder builder = new ConfigurationBuilder();
	      builder.clustering().cacheMode(CacheMode.DIST_SYNC);
	      builder.encoding().key().mediaType(MediaType.APPLICATION_OBJECT_TYPE);
	      builder.encoding().value().mediaType(MediaType.APPLICATION_OBJECT_TYPE);
	      
	      /**
	       * TODO: Transactional test
	       */
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
	      
	      RemoteStoreConfigurationBuilder store = builder.persistence().addStore(RemoteStoreConfigurationBuilder.class);
	      store.hotRodWrapping(false).rawValues(false)
	            .marshaller(JBossUserMarshaller.class)
	            .protocolVersion(PROTOCOL_VERSION_26)
	            .remoteCacheName(CACHE_NAME).shared(true)
	            .addServer().host("localhost").port(11223);
	      

	      GlobalConfigurationBuilder gcb = GlobalConfigurationBuilder.defaultClusteredBuilder();
	      gcb.globalJmxStatistics().jmxDomain("application-destination-two");
	      gcb.serialization().marshaller(new JBossUserMarshaller());     
	      GlobalConfiguration globalConfiguration = gcb.build();
	      DefaultCacheManager cacheManager = new DefaultCacheManager(globalConfiguration, true);
	      cacheManager.defineConfiguration(CACHE_NAME, builder.build());
	      
//	      RestServerConfiguration restServerConfiguration = new RestServerConfigurationBuilder()
//	              .host("0.0.0.0")
//	              .port(Util.getPortOffset() + 8888)
//	              .build();
//
//	        RestServer restServer = new RestServer();
//	        restServer.start(restServerConfiguration, cacheManager);
//	        System.out.println("Server REST started on port " + restServer.getPort());

	      Cache<Integer, CustomObject> cache = cacheManager.getCache(CACHE_NAME);
	      //RollingUpgradeManager rum = cache.getAdvancedCache().getComponentRegistry().getComponent(RollingUpgradeManager.class);
	      RollingUpgradeManager rum = cacheManager.getGlobalComponentRegistry().getNamedComponentRegistry(CACHE_NAME).getLocalComponent(RollingUpgradeManager.class);
	      long migrated;
	      
	      LOGGER.info("********************** START MIGRATING CACHES***********************");
		try {
			migrated = rum.synchronizeData("hotrod");
			LOGGER.log(Level.INFO,"Migrated " + migrated + " entries");
		    rum.disconnectSource("hotrod");
		    cache.forEach((key, value) -> LOGGER.log(Level.INFO, key + " -> " + value));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@PreDestroy
	public void cleanup()
	{
		
	}
}
