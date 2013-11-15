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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class GsonWrapper {
	
	@SuppressWarnings("unused")
	private static final Logger logger = Logger.getLogger(GsonWrapper.class.getCanonicalName());
	private GsonBuilder gsonBuilder;
	private Gson gson;
	
	private class DateTimeTypeConverter implements JsonSerializer<DateTime>, JsonDeserializer<DateTime> {

		  public JsonElement serialize(DateTime src, Type srcType, JsonSerializationContext context) {
		    return new JsonPrimitive(src.toString());
		  }
		  public DateTime deserialize(JsonElement json, Type type, JsonDeserializationContext context)
		      throws JsonParseException {
		    return new DateTime(json.getAsString());
		  }
	}
	
	private class LocalDateTimeTypeConverter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {

		  public JsonElement serialize(LocalDateTime src, Type srcType, JsonSerializationContext context) {
			DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
			return new JsonPrimitive(fmt.print(src));
		  }

		  public LocalDateTime deserialize(JsonElement json, Type type, JsonDeserializationContext context)
		      throws JsonParseException {
		    return new LocalDateTime(json.getAsString());
		  }
	}
	
	private class DateTypeConverter implements JsonSerializer<Date>, JsonDeserializer<Date> {

		  public JsonElement serialize(Date src, Type srcType, JsonSerializationContext context) {
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
			DateTime dt = DateTime.parse(df.format(src));
		    //return new JsonPrimitive(src.toString());
			return new JsonPrimitive(dt.toString());
		  }

		  public Date deserialize(JsonElement json, Type type, JsonDeserializationContext context)
		      throws JsonParseException {
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
			Date dt= null;
			try {
			 dt = df.parse(json.getAsString());

			} catch(ParseException ex) { throw new JsonParseException("util.Date conversion failed");}
			 return dt;
		  }
	}
    
    public GsonWrapper() {
    	gsonBuilder = new GsonBuilder();
    	gsonBuilder.registerTypeAdapter(DateTime.class, new DateTimeTypeConverter());
    	gsonBuilder.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeConverter());
    	gsonBuilder.registerTypeAdapter(Date.class, new DateTypeConverter());
    	gson = gsonBuilder.create();
    }
    
    public Gson getGson() {
    	return gson;
    }

}
