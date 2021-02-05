/**
 * @author Meissa
 */
package com.redhat.consulting.jdg.rollingupgrade.source;

/**
 * @author Meissa
 */
public class MigrationConfig {
	
	public static final String HOTROD_BIND_ADRESSS = System.getProperty("hotrod.bind.address", "0.0.0.0") ;
	public static final int HOTROD_LISTEN_PORT = Integer.parseInt( System.getProperty("hotrod.listen.port", "11223") );
	

}
