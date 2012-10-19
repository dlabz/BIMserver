package org.bimserver.shared.json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.bimserver.shared.meta.SBase;
import org.bimserver.shared.meta.SClass;
import org.bimserver.shared.meta.SField;
import org.bimserver.shared.meta.ServicesMap;
import org.codehaus.jettison.json.JSONException;

import com.google.common.base.Charsets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;

public class JsonConverter {

	private final ServicesMap servicesMap;

	public JsonConverter(ServicesMap servicesMap) {
		this.servicesMap = servicesMap;
	}
	
	public void toJson(Object object, JsonWriter out) throws IOException {
		if (object instanceof SBase) {
			SBase base = (SBase)object;
			out.beginObject();
			out.name("__type");
			out.value(base.getSClass().getSimpleName());
			for (SField field : base.getSClass().getAllFields()) {
				out.name(field.getName());
				toJson(base.sGet(field), out);
			}
			out.endObject();
		} else if (object instanceof Collection) {
			Collection<?> collection = (Collection<?>)object;
			out.beginArray();
			for (Object value : collection) {
				toJson(value, out);
			}
			out.endArray();
		} else if (object instanceof Date) {
			out.value(((Date)object).getTime());
		} else if (object instanceof DataHandler) {
			DataHandler dataHandler = (DataHandler)object;
			try {
				InputStream inputStream = dataHandler.getInputStream();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				IOUtils.copy(inputStream, baos);
				out.value(new String(Base64.encodeBase64(baos.toByteArray()), Charsets.UTF_8));
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (object instanceof String) {
			out.value((String)object);
		} else if (object instanceof Number) {
			out.value((Number)object);
		} else if (object instanceof Enum) {
			out.value(object.toString());
		} else if (object instanceof Boolean) {
			out.value((Boolean)object);
		} else if (object == null) {
			out.nullValue();
		} else {
			throw new UnsupportedOperationException(object.toString());
		}
	}
	
	public JsonElement toJson(Object object) throws JSONException {
		if (object instanceof SBase) {
			SBase base = (SBase)object;
			JsonObject jsonObject = new JsonObject();
			jsonObject.add("__type", new JsonPrimitive(base.getSClass().getSimpleName()));
			for (SField field : base.getSClass().getAllFields()) {
				jsonObject.add(field.getName(), toJson(base.sGet(field)));
			}
			return jsonObject;
		} else if (object instanceof Collection) {
			Collection<?> collection = (Collection<?>)object;
			JsonArray jsonArray = new JsonArray();
			for (Object value : collection) {
				jsonArray.add(toJson(value));
			}
			return jsonArray;
		} else if (object instanceof Date) {
			return new JsonPrimitive(((Date)object).getTime());
		} else if (object instanceof DataHandler) {
			DataHandler dataHandler = (DataHandler)object;
			try {
				InputStream inputStream = dataHandler.getInputStream();
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				IOUtils.copy(inputStream, out);
				return new JsonPrimitive(new String(Base64.encodeBase64(out.toByteArray()), Charsets.UTF_8));
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (object instanceof Boolean) {
			return new JsonPrimitive((Boolean)object);
		} else if (object instanceof String) {
			return new JsonPrimitive((String)object);
		} else if (object instanceof Long) {
			return new JsonPrimitive((Long)object);
		} else if (object instanceof Integer) {
			return new JsonPrimitive((Integer)object);
		} else if (object instanceof Enum) {
			return new JsonPrimitive(object.toString());
		} else if (object == null) {
			return JsonNull.INSTANCE;
		} else if (object instanceof byte[]) {
			byte[] data = (byte[])object;
			return new JsonPrimitive(new String(Base64.encodeBase64(data), Charsets.UTF_8));
		}
		throw new UnsupportedOperationException(object.toString());
	}

	public Object fromJson(SClass definedType, SClass genericType, Object object) throws JSONException, ConvertException {
		if (object instanceof JsonObject) {
			JsonObject jsonObject = (JsonObject) object;
			if (jsonObject.has("__type")) {
				String type = jsonObject.get("__type").getAsString();
				SClass sClass = servicesMap.getType(type);
				SBase newObject = sClass.newInstance();
				for (SField field : newObject.getSClass().getAllFields()) {
					if (jsonObject.has(field.getName())) {
						newObject.sSet(field, fromJson(field.getType(), field.getGenericType(), jsonObject.get(field.getName())));
					}
				}
				return newObject;
			} else {
				if (jsonObject.entrySet().size() != 0) {
					throw new ConvertException("Missing __type field in " + jsonObject.toString());
				} else if (definedType.isVoid()) {
					return null;
				} else {
					throw new UnsupportedOperationException();
				}
			}
		} else if (object instanceof JsonArray) {
			JsonArray array = (JsonArray)object;
			List<Object> list = new ArrayList<Object>();
			for (int i=0; i<array.size(); i++) {
				list.add(fromJson(definedType, genericType, array.get(i)));
			}
			return list;
		} else if (object instanceof JsonNull) {
			return null;
		} else if (definedType.isByteArray()) {
			if (object instanceof JsonPrimitive) {
				JsonPrimitive jsonPrimitive = (JsonPrimitive)object;
				return Base64.decodeBase64(jsonPrimitive.getAsString().getBytes(Charsets.UTF_8));
			}
		} else if (definedType.isDataHandler()) {
			if (object instanceof JsonPrimitive) {
				JsonPrimitive jsonPrimitive = (JsonPrimitive)object;
				byte[] data = Base64.decodeBase64(jsonPrimitive.getAsString().getBytes(Charsets.UTF_8));
				try {
					DataHandler dataHandler = new DataHandler(new ByteArrayDataSource(new ByteArrayInputStream(data), null));
					return dataHandler;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else if (definedType.isInteger()) {
			if (object instanceof JsonPrimitive) {
				return ((JsonPrimitive)object).getAsInt();
			}
		} else if (definedType.isLong()) {
			if (object instanceof JsonPrimitive) {
				return ((JsonPrimitive)object).getAsLong();
			}
		} else if (definedType.isEnum()) {
			JsonPrimitive primitive = (JsonPrimitive)object;
			for (Object enumConstantObject : definedType.getInstanceClass().getEnumConstants()) {
				Enum<?> enumConstant = (Enum<?>)enumConstantObject;
				if (enumConstant.name().equals(primitive.getAsString())) {
					return enumConstant;
				}
			}
		} else if (definedType.isDate()) {
			if (object instanceof JsonPrimitive) {
				return new Date(((JsonPrimitive)object).getAsLong());
			}
		} else if (definedType.isString()) {
			if (object instanceof JsonPrimitive) {
				return ((JsonPrimitive)object).getAsString();
			} else if (object instanceof JsonNull) {
				return null;
			}
		} else if (definedType.isBoolean()) {
			if (object instanceof JsonPrimitive) {
				return ((JsonPrimitive)object).getAsBoolean();
			}
		} else if (definedType.isList()) {
			if (genericType.isLong()) {
				if (object instanceof JsonPrimitive) {
					return ((JsonPrimitive)object).getAsLong();
				}
			} else if (genericType.isInteger()) {
				if (object instanceof JsonPrimitive) {
					return ((JsonPrimitive)object).getAsInt();
				}
			}
		} else if (definedType.isDouble()) {
			if (object instanceof JsonPrimitive) {
				return ((JsonPrimitive)object).getAsDouble();
			}
		} else if (definedType.isFloat()) {
			if (object instanceof JsonPrimitive) {
				return ((JsonPrimitive)object).getAsFloat();
			}
		} else if (definedType.isVoid()) {
			return null;
		}
		throw new UnsupportedOperationException(object.toString());
	}
}