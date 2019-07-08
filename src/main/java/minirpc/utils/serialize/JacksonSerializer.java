package minirpc.utils.serialize;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import minirpc.utils.RPCException;

import java.io.IOException;

public class JacksonSerializer implements Serializer {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	static {
		/* default :
		这个特性，决定了解析器是否将自动关闭那些不属于parser自己的输入源。 如果禁止，则调用应用不得不分别去关闭那些被用来创建parser的基础输入流InputStream和reader；
		如果允许，parser只要自己需要获取closed方法（当遇到输入流结束，或者parser自己调用 JsonParder#close方法），就会处理流关闭。
		注意：这个属性默认是true，即允许自动关闭流
		AUTO_CLOSE_SOURCE(true),

		该特性决定parser将是否允许解析使用Java/C++ 样式的注释（包括'/'+'*' 和'//' 变量）。 由于JSON标准说明书上面没有提到注释是否是合法的组成，所以这是一个非标准的特性；
		尽管如此，这个特性还是被广泛地使用。
		注意：该属性默认是false，因此必须显式允许，即通过JsonParser.Feature.ALLOW_COMMENTS 配置为true。
        ALLOW_COMMENTS(false),

		ALLOW_YAML_COMMENTS(false),

		这个特性决定parser是否将允许使用非双引号属性名字.（这种形式在Javascript中被允许，但是JSON标准说明书中没有）。
		注意：由于JSON标准上需要为属性名称使用双引号，所以这也是一个非标准特性，默认是false的。
		同样，需要设置JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES为true，打开该特性。
		ALLOW_UNQUOTED_FIELD_NAMES(false),

		特性决定parser是否允许单引号来包住属性名称和字符串值。
		注意：默认下，该属性也是关闭的。需要设置JsonParser.Feature.ALLOW_SINGLE_QUOTES为true
        ALLOW_SINGLE_QUOTES(false),

        该特性决定parser是否允许JSON字符串包含非引号控制字符（值小于32的ASCII字符，包含制表符和换行符）。 如果该属性关闭，则如果遇到这些字符，则会抛出异常。
		JSON标准说明书要求所有控制符必须使用引号，因此这是一个非标准的特性。
		注意：默认时候，该属性关闭的。需要设置：JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS为true。
        ALLOW_UNQUOTED_CONTROL_CHARS(false),

        该特性可以允许接受所有引号引起来的字符，使用‘反斜杠\’机制：如果不允许，只有JSON标准说明书中 列出来的字符可以被避开约束。
		由于JSON标准说明中要求为所有控制字符使用引号，这是一个非标准的特性，所以默认是关闭的。
		注意：一般在设置ALLOW_SINGLE_QUOTES属性时，也设置了ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER属性，
		所以，有时候，你会看到不设置ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER为true，但是依然可以正常运行。
        ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER(false),

        该特性决定parser是否允许JSON整数以多个0开始(比如，如果000001赋值给json某变量，
		如果不设置该属性，则解析成int会抛异常报错：org.codehaus.jackson.JsonParseException: Invalid numeric value: Leading zeroes not allowed)
		注意：该属性默认是关闭的，如果需要打开，则设置JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS为true。
        ALLOW_NUMERIC_LEADING_ZEROS(false),

        该特性允许parser可以识别"Not-a-Number" (NaN)标识集合作为一个合法的浮点数。 例如： allows (tokens are quoted contents, not including quotes):
		"INF" (for positive infinity), as well as alias of "Infinity"
		"-INF" (for negative infinity), alias "-Infinity"
		"NaN" (for other not-a-numbers, like result of division by zero)
        ALLOW_NON_NUMERIC_NUMBERS(false),

        ALLOW_MISSING_VALUES(false),
        ALLOW_TRAILING_COMMA(false),
        STRICT_DUPLICATE_DETECTION(false),
        IGNORE_UNDEFINED(false),
        INCLUDE_SOURCE_IN_LOCATION(true);
        */
		objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS,true);

		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
	}

	@Override
	public <T> byte[] serialize(T obj) {
		if(obj == null)
			throw new RPCException("Jackson serialize obj is null...");
		try {
			return objectMapper.writeValueAsBytes(obj);
		}catch (JsonProcessingException e) {
			throw new RPCException(e);
		}
	}

	@Override
	public <T> T deserialize(byte[] data, Class<T> clazz) {
		if(data == null || data.length == 0)
			throw new RPCException("Jackson deserialize byte[] is null or empty...");
		if(clazz == null)
			throw new RPCException("Class type is null...");

		try{
			//return objectMapper.convertValue(data,clazz);
			return objectMapper.readValue(data,clazz);
		} catch (IOException e) {
			throw new RPCException(e);
		}
	}
}
