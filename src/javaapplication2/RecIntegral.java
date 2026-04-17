/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package javaapplication2;

import java.io.Serializable;

public class RecIntegral implements Serializable {
    private static final long serialVersionUID = 1L;
    
    static int counter = 0;
    int id;
    double a;
    double b;
    double h;
    double result;

    public RecIntegral(double a, double b, double h, double result) throws InvalidException{
        this.a = a;
        this.b = b;
        this.h = h;
        this.result = result;
        this.id = counter++;
    }
    
    private boolean isValid(double x) {
        return x >= 0.000001 && x <= 1_000_000;
    }
}

    
