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


import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.cmd.Query;

/**
 * Refer to the JSON API specification under
 * http://cloudburo.github.io/docs/opensource/jsonapispec
 */
@SuppressWarnings("serial")
public abstract class RestAPIServlet extends HttpServlet {
	
	private static final Logger logger = Logger.getLogger(RestAPIServlet.class.getCanonicalName());
	protected static int sResponseLimit = 20;
	
	private String fields=null;
	private String filter=null;
	
	protected class MetaRecord {
		String _cursor;
		MetaRecord(String cursor) {
			_cursor = cursor;
		}
	}
	
	/** 
	 * Method allows to set the number of objects returned. 
	 * Helpful for testing purposes **/
	public static void setResponseResultSize(int limit) {sResponseLimit = limit;}
	
	@SuppressWarnings("rawtypes")
	protected abstract Class getPersistencyClass();
	
	protected abstract  Objectify ofy();
	
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {
		logger.log(Level.FINER, "Call with following path {0}", req.getPathInfo());
		fields= req.getParameter("fields");
		filter= req.getParameter("filter");
		logger.log(Level.INFO, "Field parameter {0}", fields);
		logger.log(Level.INFO, "Filter parameter {0}", filter);
		logger.log(Level.INFO, "Going to fetch {0} objects", getPersistencyClass().getName());
		if (req.getPathInfo() == null || req.getPathInfo().length()==1) {
			getCollection(getPersistencyClass(),req,resp);
		} else {
			getObject(getPersistencyClass(),req,resp);	
		}
	}
	
	@SuppressWarnings("unchecked")
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.log(Level.INFO, "Updating Customer");
		Object obj = (new GsonWrapper()).getGson().fromJson(req.getReader(),getPersistencyClass());
		ofy().save().entity(obj).now();
		//logger.log(Level.INFO, "Persisted Customer with id {0}",customer._id);
		resp.getWriter().print((new GsonWrapper()).getGson().toJson(obj));
	}
	
	@SuppressWarnings("unchecked")
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.log(Level.INFO, "Creating Customer");
		Object obj = (new GsonWrapper()).getGson().fromJson(req.getReader(),getPersistencyClass());
		ofy().save().entity(obj).now();
		//logger.log(Level.INFO, "Persisted Customer with id {0}",customer._id);
		resp.getWriter().print((new GsonWrapper()).getGson().toJson(obj));
	}
	
	@SuppressWarnings("unchecked")
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		Key<?> objectKey = Key.create(getPersistencyClass(), Long.parseLong(req.getPathInfo().substring(1)));
		logger.log(Level.INFO, "Deleting object with identifier {0}",  objectKey);
		ofy().delete().key(objectKey).now();
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes", "static-access" })
	private void getCollection(Class clazz, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		StringBuffer buf = new StringBuffer("[");
		Query<?> query = ofy().load().type(clazz).limit(sResponseLimit);
		String cursorStr = req.getParameter("cursor");
		if (cursorStr != null) 
			query = query.startAt(Cursor.fromWebSafeString(cursorStr));
		int nrRec = 0;
		QueryResultIterator<?> iterator = query.iterator();
		Collection collection = new ArrayList();		
		while (iterator.hasNext()) {
			nrRec++;
			if (fields == null || fields.equals("")) {
				collection.add(iterator.next());
			} else {
				Object obj = iterator.next();
				try {
					buf.append(partialResponse(fields,obj));
				    buf.append(",");
				} catch (Exception e) {
					resp.sendError(resp.SC_BAD_REQUEST);
					resp.getWriter().print(errorMsg(e.getMessage(),"0","0"));
					return;
					// This shouldn't happen at all IllegalAccessException
				}
			}
		}

		resp.setContentType("application/json");
		String cursor="";
		if (nrRec==sResponseLimit) {
			cursor = iterator.getCursor().toWebSafeString();
		}
		MetaRecord metaObj = new MetaRecord(cursor); 
		if (fields == null || fields.equals("")) {
			collection.add(metaObj);
			resp.getWriter().print((new GsonWrapper()).getGson().toJson(collection));
		} else {
			buf.append((new GsonWrapper()).getGson().toJson(metaObj));
			buf.append("]");
			resp.getWriter().print(buf.toString());
		}
		
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked", "static-access" })
	private void getObject(Class clazz, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		StringTokenizer tok = new StringTokenizer(req.getPathInfo(),"/");
		// This must be the identifier
		if (tok.countTokens() == 1) {
			Key<?> key = Key.create(clazz, Long.parseLong(tok.nextToken()));
			logger.log(Level.INFO, "Going to get customer {0}", key);
			Object businessObj = ofy().load().type(clazz).filterKey(key).first().now();
			if (businessObj != null) {
				if (fields == null || fields.equals(""))
					resp.getWriter().print((new GsonWrapper()).getGson().toJson(businessObj));
				else
					try {
						resp.getWriter().print(partialResponse(fields,businessObj));
					} catch (Exception e) {
						resp.sendError(resp.SC_BAD_REQUEST);
						resp.getWriter().print(errorMsg(e.getMessage(),"0","0"));
						// This shouldn't happen at all IllegalAccessException
					}
			}
			else {
				resp.getWriter().print("{}");
			    resp.setStatus(resp.SC_NOT_FOUND);
			}
		}  else {
			// FIXME: We have to throw an error that we don't understand this
			resp.sendError(resp.SC_BAD_REQUEST);
		}
	}
	
	protected String errorMsg (String usrMsg, String code, String moreInfo) {
		StringBuffer buf = new StringBuffer("{");
		buf.append("message: \"").append(usrMsg).append("\"\n");
		buf.append("errorCode: \"").append(code).append("\"\n");
		buf.append("moreInfo: \"").append(moreInfo).append("\"\n");
		buf.append("}"); 
		return buf.toString();
	}
	
	private String partialResponse(String filterList, Object elem) throws Exception  {
		Vector<String> fieldVec = new Vector<String>();
		StringTokenizer tok = new StringTokenizer(filterList,",");
		while (tok.hasMoreTokens()) fieldVec.add(tok.nextToken());
		Field[] fields = elem.getClass().getDeclaredFields();
		StringBuffer buf = new StringBuffer("{");
		for (int i=0; i< fields.length; i++) {
			if (fieldVec.indexOf(fields[i].getName()) >= 0) {
				logger.log(Level.INFO, "Adding Field {0}",fields[i].getName()); 
				Object value = fields[i].get(elem);
				buf.append("\""+fields[i].getName()+"\""+":"+(new GsonWrapper()).getGson().toJson(value));
				buf.append(",\n");
			} 
		}
		buf.replace(buf.length()-2, buf.length()-1, "");
		buf.append("}");
		logger.log(Level.INFO, "Returning JSON {0}",buf.toString()); 
		return buf.toString();
	}
}
