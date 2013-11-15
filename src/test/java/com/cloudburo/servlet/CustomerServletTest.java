/**
 * The MIT License (MIT)
 *
 * Copyright (C) 2013 Felix Kuestahler <felix@cloudburo.com> http://cloudburo.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated 
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, 
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of 
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO 
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, 
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. 
 */

package com.cloudburo.servlet;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudburo.entity.Customer;
import com.cloudburo.entity.CustomerServlet;
import com.google.appengine.api.datastore.dev.LocalDatastoreService;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.joda.time.LocalDateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CustomerServletTest {

	private CustomerServlet customerServlet;
	private static final Logger logger = Logger.getLogger(CustomerServlet.class.getCanonicalName());
	
	private final LocalServiceTestHelper helper =
		      new LocalServiceTestHelper(new LocalUserServiceTestConfig())
		          .setEnvIsLoggedIn(true)
		          .setEnvAuthDomain("localhost")
		          .setEnvEmail("test@localhost");
	
	  @SuppressWarnings("static-access")
	  @Before
	  public void setupCustomerServlet() {
	    helper.setUp();
	    LocalDatastoreService dsService = (LocalDatastoreService)helper.getLocalService(LocalDatastoreService.PACKAGE);
	    // Set to false if you want to persist the data
	    dsService.setNoStorage(true);
	    customerServlet = new CustomerServlet();
	    // We set the response result size to 3 to simulate the paging
	 	CustomerServlet.setResponseResultSize(3);
	  }
	  
	  @After
	  public void tearDownHelper() {
	    helper.tearDown();
	  }
	  
	  @Test
	  public void baseOperations() throws IOException, ServletException {
	    
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
	    
	    // A customer object test entry
	    Customer customerIn = new Customer();
	    customerIn.name = "Felix";
	    customerIn.address = "Kuestahler";
	    customerIn.date = new Date();
	    customerIn.date1 = new LocalDateTime();
	    String customerInJSON = (new GsonWrapper()).getGson().toJson(customerIn);
	    
	    logger.log(Level.INFO, "Going to persist {0}", customerInJSON);
	    StringWriter stringWriter = new StringWriter();
	    
	    // TEST: Check the persistence operations "POST"
	    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(customerInJSON)));
	    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
	    
	    customerServlet.doPost(request, response);

	    Customer customerOut =  (new GsonWrapper()).getGson().fromJson(stringWriter.toString(), Customer.class); 
	    assertEquals("Checking name", customerOut.name, customerIn.name);
	    assertEquals("Checking id", customerOut._id > 0,true);
	    
	    // TEST: Check the retrieval of the collections "GET" 
	    stringWriter = new StringWriter();
	    when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
	    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
	    
	    customerServlet.doGet(request, response);
	    
	    // Expect an array with the last entry must be a meta data record (with a attribute _cursor)
	    JsonArray array = (new JsonParser()).parse(stringWriter.toString()).getAsJsonArray();
	    assertEquals("Checking received numbers of JSON Elements", 2,array.size());
	    JsonObject elem  = array.get(array.size()-1).getAsJsonObject();
	    assertEquals("Checking that there is a 'cursor' elemen",elem.get("_cursor").getAsString().equals(""),true);

	    // TEST: Check the retrieval of a single entry "GET"
	    stringWriter = new StringWriter();
	    when(request.getPathInfo()).thenReturn("/"+customerOut._id);
	    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
	    
	    customerServlet.doGet(request, response);
	    
	    // Expect a single entry which can be converted in our domain object and correct attributes
	    customerOut =  (new GsonWrapper()).getGson().fromJson(stringWriter.toString(), Customer.class);
	    assertEquals("Checking Name", customerOut.name, customerIn.name);
	    assertEquals("Checking Surname", customerOut.surname, customerIn.surname);
	    
	    // TEST: Going to delete the entry "DELETE"
	    stringWriter = new StringWriter();
	    when(request.getPathInfo()).thenReturn("/"+customerOut._id);
	    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
	    
	    customerServlet.doDelete(request, response);

	    // We shouldn't get any data back (i.e. deleted)
	    stringWriter = new StringWriter();
	    when(request.getPathInfo()).thenReturn("/"+customerOut._id);
	    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
	    
	    customerServlet.doGet(request, response);
	    
	    assertEquals("Checking empty JSON","{}", stringWriter.toString());
	  }

}
