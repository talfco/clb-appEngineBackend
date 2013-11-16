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
import java.util.Iterator;
import java.util.Map;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CustomerServletTest {
	private static final Logger logger = Logger.getLogger(CustomerServlet.class.getCanonicalName());
	
	private CustomerServlet customerServlet;	
	
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

		Date inDate = new Date();
		LocalDateTime in1Date = new LocalDateTime();
	    // A customer object test entry
	    Customer customerIn = new Customer();
	    customerIn.name = "Felix";
	    customerIn.address = "Test";
	    customerIn.date = inDate;
	    customerIn.date1 = in1Date;

	    // TEST: Check the persistence operations "POST"
	    Customer customerOut =  persistTestRecord(customerIn); 
	    assertEquals("Checking name", customerOut.name, customerIn.name);
	    assertEquals("Checking id", customerOut._id > 0,true);
	    
	    // TEST: Check the retrieval of the collections "GET"
	    JsonArray array = getTestCollection(null);
	    // Expect an array with the last entry must be a meta data record (with a attribute _cursor)
	    assertEquals("Checking received numbers of JSON Elements", 2,array.size());
	    JsonObject elem  = array.get(array.size()-1).getAsJsonObject();
	    assertEquals("Checking that there is a 'cursor' elemen",elem.get("_cursor").getAsString().equals(""),true);

	    // TEST: Check the retrieval of a single entry "GET"
	    // Expect a single entry which can be converted in our domain object and correct attributes
	    customerOut =  getTestRecord(customerOut._id,null);
	    assertEquals("Checking Name", customerOut.name, customerIn.name);
	    assertEquals("Checking Surname", customerOut.surname, customerIn.surname);
	    assertEquals("Checking Date", customerOut.date,inDate);
	    assertEquals("Checking Joda DateTime", customerOut.date1,in1Date);
	    
	    // TEST: Going to delete the entry "DELETE"
	    StringWriter outputStringWriter = new StringWriter();
	    when(request.getPathInfo()).thenReturn("/"+customerOut._id);
	    when(response.getWriter()).thenReturn(new PrintWriter(outputStringWriter));
	    customerServlet.doDelete(request, response);

	    // TEST: We shouldn't get any data back (i.e. deleted)
	    outputStringWriter =  getTestRecordStringWriter(customerOut._id);
	    assertEquals("Checking empty JSON","{}", outputStringWriter.toString());
	    
	    // TEST: Paging functionality
	    // Add 4 records
	    customerIn = new Customer();
	    customerIn.name = "Name1";
	    customerIn.surname = "Surname1";
	    customerIn.address = "Address1"; 
	    persistTestRecord(customerIn);
	    customerIn.name = "Name2";
	    customerIn.surname = "Surname2";
	    customerIn.address = "Address2"; 
	    persistTestRecord(customerIn);
	    customerIn.name = "Name3";
	    customerIn.surname = "Surname3";
	    customerIn.address = "Address3"; 
	    persistTestRecord(customerIn);
	    customerIn.name = "Name4";
	    customerIn.surname = "Surname4";
	    customerIn.address = "Address4"; 
	    persistTestRecord(customerIn);
	    array = getTestCollection(null);
	    assertEquals("Checking received numbers of JSON Elements (3 record and 1 meta)", 4,array.size());
	    elem  = array.get(array.size()-1).getAsJsonObject();
	    assertEquals("Checking that there is a 'cursor' element isn't empty",elem.get("_cursor").getAsString().equals(""),false);
	    Hashtable<String,String> hash = new Hashtable<String, String>();
		hash.put("cursor", elem.get("_cursor").getAsString());
	    array = getTestCollection(hash);
	    assertEquals("Checking received numbers of JSON Elements (1 record and 1 meta)", 2,array.size());
	    elem  = array.get(array.size()-1).getAsJsonObject();
	    assertEquals("Checking that there is a  empty 'cursor' element","",elem.get("_cursor").getAsString());
	    elem  = array.get(array.size()-2).getAsJsonObject();
	    assertEquals("Checking that the last element is the last entered","Name4",elem.get("name").getAsString());

	    // TEST: Field Parameter for single Object
	    // Last record of the paging beforehand
	    hash = new Hashtable<String, String>();
		hash.put("fields", "name");
	    customerOut =  getTestRecord(elem.get("_id").getAsLong(),hash);
	    assertEquals("Checking that there is a 'name' field ","Name4",customerOut.name);
	    assertEquals("Checking that there is a 'surname' field is",null,customerOut.surname);
	    assertEquals("Checking that there is a '_id' field is",null,customerOut._id);
		hash.put("fields", "name,_id");
	    customerOut =  getTestRecord(elem.get("_id").getAsLong(),hash);
		assertEquals("Checking that there is a '_id' field is",false,customerOut._id.equals(""));
	    
	    // TEST: Field Parameter for Collection
	    array = getTestCollection(hash);
	    elem  = array.get(0).getAsJsonObject();
	    assertEquals("Checking that there is a 'name' field ","Name1",elem.get("name").getAsString());
	    assertEquals("Checking that there is no 'surname' field ",null,elem.get("surname"));
	    
	  } 
	  
	  private Customer persistTestRecord(Customer customerIn) throws IOException, ServletException {
		  HttpServletRequest request = mock(HttpServletRequest.class);
		  HttpServletResponse response = mock(HttpServletResponse.class);
		  String customerInJSON = (new GsonWrapper()).getGson().toJson(customerIn);
		  logger.log(Level.INFO, "Going to persist {0}", customerInJSON);
		  StringWriter outputStringWriter = new StringWriter();
		  when(request.getReader()).thenReturn(new BufferedReader(new StringReader(customerInJSON)));
		  when(response.getWriter()).thenReturn(new PrintWriter(outputStringWriter));
		  customerServlet.doPost(request, response);
		  return (new GsonWrapper()).getGson().fromJson(outputStringWriter.toString(), Customer.class); 	  
	  }
	  
	  private JsonArray getTestCollection(Map<String,String> params) throws IOException, ServletException {
		  HttpServletRequest request = mock(HttpServletRequest.class);
		  HttpServletResponse response = mock(HttpServletResponse.class);
		  StringWriter outputStringWriter = new StringWriter();
		  when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
		  when(response.getWriter()).thenReturn(new PrintWriter(outputStringWriter));
		  when(request.getPathInfo()).thenReturn("/");
		  injectParams(request,params);
		  /*
		  if (params != null) {
			  Iterator<String> it = params.keySet().iterator();
			  while (it.hasNext()) {
				  String key = it.next();
				  when(request.getParameter(key)).thenReturn(params.get(key));
			  }
		  }*/ 
		  customerServlet.doGet(request, response);
		  return  (new JsonParser()).parse(outputStringWriter.toString()).getAsJsonArray(); 	  
	  }
	  
	  private Customer getTestRecord(Long id,Map<String,String> params) throws IOException, ServletException {
		  HttpServletRequest request = mock(HttpServletRequest.class);
		  HttpServletResponse response = mock(HttpServletResponse.class);
		  StringWriter outputStringWriter = new StringWriter();
		  when(request.getPathInfo()).thenReturn("/"+id);
		  when(response.getWriter()).thenReturn(new PrintWriter(outputStringWriter));
		  injectParams(request,params);
		  customerServlet.doGet(request, response);
		  return  (new GsonWrapper()).getGson().fromJson(outputStringWriter.toString(), Customer.class);
	  }
	  
	  private StringWriter getTestRecordStringWriter(Long id) throws IOException, ServletException {
		  HttpServletRequest request = mock(HttpServletRequest.class);
		  HttpServletResponse response = mock(HttpServletResponse.class);
		  StringWriter outputStringWriter = new StringWriter();
		  when(request.getPathInfo()).thenReturn("/"+id);
		  when(response.getWriter()).thenReturn(new PrintWriter(outputStringWriter));
		    customerServlet.doGet(request, response);
		    return  outputStringWriter;
	  }
	  
	  private void injectParams(HttpServletRequest request,Map<String,String> params) {
		  if (params != null) {
			  Iterator<String> it = params.keySet().iterator();
			  while (it.hasNext()) {
				  String key = it.next();
				  when(request.getParameter(key)).thenReturn(params.get(key));
			  }
		  }		  
	  }
	 

}
