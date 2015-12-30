# VinGris
Add configurability to your enterprise java applications.
The VinGris.xml file will incorporated into your app as soon as it is updated, no need to restart your app.

Step 1:  Build this library, or use the jar file in /dist in your classpath.
Step 2:  Build your application with the jar file from Step 1 in the distribution.
Step 3:  Create a VinGris.xml file similar to the one in /example
Step 4:  Add the -DVinGris=/path/to/VinGris.xml to your application server java command
Step 5:  Update your application for different environments (dev/staging/prod) through the config file, not the code.  Relax more.


The VinGris.xml file should look like this:
  <?xml version='1.0' encoding='utf-8'?>
  <suite name="YourCompanyName">
	  <application name="YourApplicationName">
		  <property name="config.test"  value="VinGris Configuration"/>		
	  </application>
  </suite>


Inside your java classes which will be reading configurations (one pattern is to have a ConfigurationReader that responds to requests):
	public static final String VINGRIS_SUITE = "YourCompanyName";      
	    //The above should match the "suite" element's "name" attribute in VinGris.xml
	public static final String VINGRIS_APP   = "YourApplicationName";
	    //The above should match the "application" element's "name" attribute in VinGris.xml
