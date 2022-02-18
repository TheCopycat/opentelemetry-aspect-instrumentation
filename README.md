# OpenTelemetry instrumentation using AspectJ
A poc on adding manual instrumentation over Java code without modifying source code or rebuilding.

## Requirements

Requires Maven 3 to build (2 might work also).

## Build

```
mvn clean install
```

This will produce the ```target/opentelemetry-aspect-<version>.jar```

## Configuration

The configuration of methods and package to instruments is made with an ```aop.xml``` file.
It should contains the following elements : 
- A weaver, for narrowing the package to scan for instrumentation
- The declaration of an implementation of ```com.github.thecopycat.opentelemetry.TracingAspect``` with the associated 
pointcut named ```scope```.
- An aspect of the type declared just before.

### Example aop.xml

```xml
<aspectj>
	<weaver>
		<include within="com.github.mypackage.*" />
	</weaver>
	<aspects>
		<aspect name="com.github.mypackage.ConcreteAspect"/>
		<concrete-aspect
			name="com.github.mypackage.ConcreteAspect"
			extends="com.github.thecopycat.opentelemetry.TracingAspect"
		>
			<pointcut
				name="scope"
				expression="execution(* com.github.mypackage.subpackageone.MyClass.*(..))" 
			/>
		</concrete-aspect>
	</aspects>
</aspectj>
```

You can check full pointcut semantics in [the official Aspectj documentation](https://www.eclipse.org/aspectj/doc/released/progguide/semantics-pointcuts.html)

## Running

### Prerequisites for running

- A java application
- The aspectj-weaver jar
- The opentelemetry-instrumentation jar
- An open telemetry collector, with some visualization tools.

### Adding agent

To enable the instrumentation to your java application, you need at least :  
- to add ```opentelemetry-aspect-<version>.jar``` in your application classpath
- to add the following to the java command line ```-javaagent:<path/to/opentelemetry-instrumentation.jar>
  -javaagent:<path/to/aspectjweaver.jar> -Dorg.aspectj.weaver.loadtime.configuration=file:/<path/to/aop.xml> 
  -Dotel.resource.attributes=service.name=<name-of-your-app-service>``` 

Example : 
```java -cp opentelemetry-aspect-1.0-SNAPSHOT.jar 
-javaagent:aspectjweaver-1.9.7.jar -Dorg.aspectj.weaver.loadtime.configuration=file:samples/aop.xml 
-javaagent:opentelemetry-javaagent-all.jar -Dotel.resource.attributes=service.name=sample-service
-jar sample/sample-java-service.jar```


