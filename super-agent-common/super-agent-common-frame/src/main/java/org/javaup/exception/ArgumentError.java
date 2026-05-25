package org.javaup.exception;

import lombok.Data;

/**
 
 * @description: 异常类

 **/

@Data
public class ArgumentError {

	private String argumentName;

	private String message;
}
