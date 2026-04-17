/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package javaapplication2;

/**
 *
 * @author admin
 */
public class InvalidException extends Exception {

    private final String fieldName;
    private final String wrongValue;

    public InvalidException(String message, String fieldName, String wrongValue) {
        super(message);
        this.fieldName = fieldName;
        this.wrongValue = wrongValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getWrongValue() {
        return wrongValue;
    }

}
