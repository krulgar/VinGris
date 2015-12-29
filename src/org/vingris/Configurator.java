package org.vingris;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.logging.Logger;

import org.apache.xerces.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * VinGris Configuration Utility
 * 
 * See http://code.google.com/p/vingris/ for more information.
 * 
 * Author: bscriber & probler
 */
public class Configurator extends DefaultHandler {
    
	private static final Logger ourLog = Logger.getLogger( 
			Thread.currentThread().getStackTrace()[0].getClassName() );

    protected static String SUITE_ELEMENT = "suite";
    protected static String APP_ELEMENT = "application";
    protected static String PROP_ELEMENT = "property";

    protected static Configurator ourHandler 		= null;
    protected static Configurator overrideHandler 	= null;

    /* Environment Variables */
    public static final String 		PATH_SEPARATOR 		= System.getProperty("file.separator");
    protected static final String 	PROPERTIES_FILE 	= "VinGris.xml";
    protected static String 		OVVERIDE_ELEMENT 	= "vingris-override-file";
    private static String 			VINGRIS_FILE 		= System.getProperty("vin.gris") +
        														PATH_SEPARATOR + PROPERTIES_FILE;
    
    private static String OVERRIDE_FILE = "";
    private static File configFile = null;
    private static File appOverrideConfigFile = null;

    /* Timestamp Variables */
    private static long globalConfigFileTimestamp;
    private static long appOverrideConfigFileTimestamp;
    private static long mostRecentTimestampCheck;
    
    protected static final boolean PRINT_ATTRIBS = true;

    /** The my current hash. */
    protected Hashtable myCurrentHash = null;
    
    /** The my suite. */
    protected Hashtable mySuite = new Hashtable();

    /**
     * Invoke this method to obtain VinGris.xml properties. If the VinGris.xml file
     * has not been loaded from a prior invocation, this method will automatically do so.
     * 
     * @param application the application
     * @param propertyName the property name
     * 
     * @return String value of the property to lookup.
     * 
     * @throws RuntimeException thrown if any problems are encountered during lookup of properties
     */
    protected static String getProperty(final String application, final String propertyName) 
    {
    	String returnString = null;

        if (propertyName == null) {
            throw new RuntimeException("VINGRIS: Illegal 'null' value for propertyName");
        } else if (application == null) {
            throw new RuntimeException("VINGRIS: Illegal 'null' value for application");
        }

        //need to check for updates to configuration file.
        //this will also initialize the hash if this is the first time through
        if(hasConfigFileBeenUpdated())
        	updateHash(application);

        final Hashtable localHash = ourHandler.getSuiteHashClone();

        if (!localHash.containsKey(application)) {
            throw new RuntimeException("VINGRIS: Application '" + application +
                "' not found in properties file.");
        }

        final Hashtable appHash = (Hashtable) localHash.get(application);

        if (appHash == null) {
            throw new RuntimeException("VINGRIS: Application '" + application +
                "' not found in properties file as an application." +
                "  There may be a <property> with the same name as this" +
                " <application>. Check your VinGris.xml. ");
        }

        Hashtable overrideLocalHash = null;
        Hashtable overrideAppHash = null;

        if (overrideHandler != null) {
            overrideLocalHash = overrideHandler.getSuiteHashClone();
            overrideAppHash = (Hashtable) overrideLocalHash.get(application);
        }

        if ((overrideAppHash != null) && !overrideAppHash.isEmpty() &&
                overrideAppHash.containsKey(propertyName)) {
            returnString = (String) overrideAppHash.get(propertyName);
        } else if ((appHash != null) && !appHash.isEmpty() &&
                appHash.containsKey(propertyName)) {
            returnString = (String) appHash.get(propertyName);
        } else {
            throw new RuntimeException("VinGris: Property '" + propertyName +
                "' not found in application '" + application + "' in file: '" +
                VINGRIS_FILE + "'");
        }
        
        try {
        	returnString = processVariableSubstitution(returnString);
        } catch(Exception e) {
        	throw new RuntimeException("VinGris: Property '" + propertyName +
            "' in file '" + VINGRIS_FILE + "': "+e);
        }

        return returnString;
    }

    /**
     * Functions identically to the getProperty() method without throwing a RuntimeException. 
     * Instead this method returns null, which allows for cleaner code and performing default 
     * behavior when a property is not found.  
     * 
     * @param application the application
     * @param propertyName the property name
     * 
     * @return the property safe
     */
    public static String getPropertySafely(final String application, final String propertyName) 
    {
    	String returnString = null;
    	try {
    		returnString = Configurator.getProperty(application, propertyName);
    	}
    	catch (final RuntimeException re)
    	{
    		ourLog.info("Error detected during VinGris property lookup.\n"+re.toString());
    		returnString = null;
    	}
    	
    	return returnString;
    }
    
    
    
	/**
	 * Process variable substitution.
	 * 
	 * @param propertyString the property string
	 * 
	 * @return the string
	 * 
	 * @throws RuntimeException the runtime exception
	 */
	public static String processVariableSubstitution(String propertyString) throws RuntimeException 
	{
		//process variable substitutions
        int index1, index2;
        String sysVar, entry=null, first, last;
        while(propertyString.indexOf("@@") != -1)
        {
        	index1 = propertyString.indexOf("@@");
        	index2 = propertyString.indexOf("@@", index1+1);
        	sysVar = propertyString.substring(index1+2, index2);
        	
        	first = propertyString.substring(0, index1);
        	last = propertyString.substring(index2+2, propertyString.length());
        	
        	try{
        		entry = System.getenv(sysVar);
        	} catch(Error er) {
        		ourLog.warning("VINGRIS: Caught unexpected error while trying to fetch environment variable."+er.toString());
        	}
        	
        	if(entry == null)
        	{
        		entry = System.getProperty(sysVar);
        		if (entry == null)
        			throw new RuntimeException("Undefined system variable or property '"+sysVar+"'");
        	}
        	
        	propertyString = new String(first.concat(entry).concat(last));
        }
		return propertyString;
	}

    /**
     * This function was created solely for the purpose of supporting
     * legacy applications which do not specify their VinGris.xml
     * file location via a runtime arguement. Instead it is expected to be in the
     * conf subdirectory under the weblogic domain.
     * 
     * @param pathname java.lang.String
     * 
     * @return java.io.File
     */
    private static File findMasterFile(final String pathname) 
    {
        File returnFile;

        try {
            returnFile = findFile(pathname);
        } catch (RuntimeException re) {
            //must be a legacy app to get here...use different path variable
        	final String LEGACY_CONFIG_SUBDIR = "conf";
        	final String LEGACY_CONF_DIR_LOCATION = System.getProperty("user.dir") +
                PATH_SEPARATOR + LEGACY_CONFIG_SUBDIR;
        	final String LEGACY_ABS_CONFIG_DIR = LEGACY_CONF_DIR_LOCATION +
                PATH_SEPARATOR + PROPERTIES_FILE;
        	ourLog.warning("VINGRIS: VinGris file location not specified. Looking in default location: " 
        			+ LEGACY_ABS_CONFIG_DIR);
            returnFile = findFile(LEGACY_ABS_CONFIG_DIR);

            VINGRIS_FILE = LEGACY_ABS_CONFIG_DIR;
        }

        return returnFile;
    }

    /**
     * Answer the file for the specified name if it exists or null.
     * 
     * @param pathname java.lang.String
     * 
     * @return java.io.File
     */
    private static File findFile(final String pathname) {
        File tempFile;

        tempFile = new File(pathname);
        
        if (tempFile.exists()) {
            return tempFile;
        } 
        throw new RuntimeException("VINGRIS: Configuration file not found: "
        		+ pathname + " (" + tempFile.getAbsolutePath()+")");
    }

    /**
     * Checks timestamps on the VinGris.xml file for updates.
     * This method also checks the override file for updates, and will perform
     * initialization if necessary.
     * 
     * @param application the application
     */
    private static void updateHash(final String application) 
    {
        if (configFile == null) 
        {
            configFile = findMasterFile(VINGRIS_FILE);
        }

        ourLog.info("VINGRIS: Reloading configuration file: " + VINGRIS_FILE);
        globalConfigFileTimestamp = configFile.lastModified();
        ourHandler = initialize(VINGRIS_FILE);

        final Hashtable localHash = ourHandler.getSuiteHashClone();
        final Hashtable appHash = (Hashtable) localHash.get(application);

        //need to check for app-specific override entry
        if ((appHash != null) &&
                ((Hashtable) appHash).containsKey(OVVERIDE_ELEMENT)) {
            //override exists - load file and populate another hash
            //if override file entry has changed or is new
            if (!OVERRIDE_FILE.equalsIgnoreCase(
                        (String) appHash.get(OVVERIDE_ELEMENT))) {
                OVERRIDE_FILE = processVariableSubstitution((String)appHash.get(OVVERIDE_ELEMENT));
                appOverrideConfigFile = findFile(OVERRIDE_FILE);
                appOverrideConfigFileTimestamp = 0;
            }

            if (appOverrideConfigFile.lastModified() != appOverrideConfigFileTimestamp) {
                ourLog.info("VINGRIS: Reloading override configuration file: " +
                    OVERRIDE_FILE);
                appOverrideConfigFileTimestamp = appOverrideConfigFile.lastModified();
                overrideHandler = initialize(OVERRIDE_FILE);
            }
        }
    }

    /**
     * *********************************************************************************************
     * Returns true if VinGris.xml has been modified since the last read. This method is publicly
     * available.
     * configFile.lastModified() is a performance intensive i/o check, so it will get executed
     * once a second at the most
     * ********************************************************************************************
     * 
     * @return true, if checks for config file been updated
     */
	public static boolean hasConfigFileBeenUpdated() {
		if (configFile == null) {
			return true;
		}
		if ((System.currentTimeMillis() > mostRecentTimestampCheck + 1000) && 
				(configFile.lastModified() != globalConfigFileTimestamp)) 
		{
			mostRecentTimestampCheck = System.currentTimeMillis();
			return true;
		}
		return false;
	}
    
    // -----------------------------------------------------------------------------
    // Below are methods that will probably never change, or are sax parsing methods
    // -----------------------------------------------------------------------------
    /**
     * Inits the.
     */
    public static void init() {
        //do nothing, but touch this class and automatically initialize the logging and configuration reader.
    }

    /**
     * End document.
     * 
     * @throws SAXException the SAX exception
     * 
     * @see org.xml.sax.helpers.DefaultHandler#endDocument()
     */
    public void endDocument() throws SAXException 
    {
    
    }

    /**
     * End element.
     * 
     * @param uri the uri
     * @param localName the local name
     * @param qName the q name
     * 
     * @throws SAXException the SAX exception
     * 
     * @see org.xml.sax.helpers.DefaultHandler#endElement(String, String, String)
     */
    public void endElement(final String uri, final String localName, final String qName)
        			throws SAXException 
    {
        //if qname is "application" then reset the current Hash.
        if (qName.equals(Configurator.APP_ELEMENT)) {
            myCurrentHash = mySuite;
        }
    }

    /**
     * Start document.
     * 
     * @throws SAXException the SAX exception
     * 
     * @see org.xml.sax.helpers.DefaultHandler#startDocument()
     */
    public void startDocument() throws SAXException 
    {
    
    }

    /**
     * Start element.
     * 
     * @param namespaceURI the namespace uri
     * @param sName the s name
     * @param qName the q name
     * @param attrs the attrs
     * 
     * @throws SAXException the SAX exception
     * 
     * @see org.xml.sax.helpers.DefaultHandler#startElement(String, String, String, Attributes)
     */
    public void startElement(String namespaceURI, String sName, String qName, Attributes attrs) 
    									throws SAXException 
    {
        String attrList = "";

        if (PRINT_ATTRIBS) {
        	final int numAttributes = attrs.getLength();

            for (int i = 0; i < numAttributes; i++) {
                attrList += (attrs.getValue(i) + "  ");
            }
        }

        String eName = sName; // element name

        if ("".equals(eName)) {
            eName = qName; // namespaceAware = false
        }

        if (sName.equals(Configurator.SUITE_ELEMENT)) {
            //this should always be called before any other elements
            //are encountered.
            myCurrentHash = mySuite;
        } else if (sName.equals(Configurator.APP_ELEMENT)) {
            //since this is the start of a new application, get the
            //name of the application and create a new hashtable, add
            //it to the current hash, and then set the current hash to
            //this new one.
            if (myCurrentHash == null) {
                throw new SAXException("Internal parsing order violated." +
                    "  Did not encounter exptected <" +
                    Configurator.SUITE_ELEMENT +
                    "> element.  Current hash is unexpectedly null.");
            }

            if ((attrs == null) || (attrs.getLength() != 1)) {
                throw new SAXException("Expected one (1) attribute in the" +
                    " <application> element.  \nFound (" + attrs.getLength() +
                    ") attributes before the unexpected end of element found.");
            }

            final Hashtable applicationHash = new Hashtable();

            myCurrentHash.put(attrs.getValue(0), applicationHash);

            myCurrentHash = applicationHash;
        } else if (sName.equals(Configurator.PROP_ELEMENT)) {
            //ensure that the current hash is not null.
            //add the attributes in this element to the current hash.
            if (myCurrentHash == null) {
                throw new SAXException("Internal parsing order violated." +
                    "  Current hash is unexpectedly null");
            }

            myCurrentHash.put(attrs.getValue(0), attrs.getValue(1));
        } else {
            //XML construction error
            throw new SAXException("Unexpected element <" + sName + "> found.");
        }
    }

    /**
     * getSuiteHashClone.
     * 
     * @return suiteHash: a CLONE of the master hash table.  This is
     * NOT the most efficient mechanism to do this and if performance
     * REALLY turns into a problem, then we can simply return a
     * reference to the real Hashtable.  Until that time, I'm not
     * comfortable with a package access method that returns the data
     * being protected by the VinGris class.
     */
    Hashtable getSuiteHashClone() 
    {
        return (Hashtable) mySuite.clone();
    }

    /**
     * Method initialize.
     * 
     * @param file the file
     * 
     * @return the VinGris
     */
    protected static Configurator initialize(final String file) 
    {
        // Use an instance of ourselves as the SAX event handler
    	final Configurator handler = new Configurator();

        // Parse the input
    	final SAXParser saxParser = new SAXParser();

        // set the handlers since we are not using javax parser
        saxParser.setContentHandler(handler);
        saxParser.setDTDHandler(handler);
        saxParser.setErrorHandler(handler);
        saxParser.setEntityResolver(handler);

        final InputStream xmlStream = readXML(file);
        final InputSource source = new InputSource(xmlStream);
       
        try{
            saxParser.parse(source);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (SAXException ex) {
        	throw new RuntimeException(ex);
    	}

        return handler;
    }

    /**
     * This method reads the runtime properties file from the
     * xml file and return it as a stream for the parser.
     * 
     * @param configPath the config path
     * 
     * @return the input stream
     */
    protected static InputStream readXML(final String configPath) 
    {
        try {
        	final FileInputStream inputStream = new FileInputStream(configPath);
            return inputStream;
        } catch (Throwable t) {
            ourLog.warning("Recieved Exception: "+t.toString());
            throw new RuntimeException(t.getMessage());
        }
    }

	/**
	 * Gets the most recent timestamp check.
	 * 
	 * @return the most recent timestamp check
	 */
	public static long getMostRecentTimestampCheck() 
	{
		return mostRecentTimestampCheck;
	}

	/**
	 * Sets the most recent timestamp check.
	 * 
	 * @param mostRecentTimestampCheck the new most recent timestamp check
	 */
	public static void setMostRecentTimestampCheck(long mostRecentTimestampCheck) 
	{
		Configurator.mostRecentTimestampCheck = mostRecentTimestampCheck;
	}
}
