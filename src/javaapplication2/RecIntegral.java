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
        if (!isValid(a) || !isValid(b) || !isValid(h)) {
            throw new InvalidException(
                    "Значения должны быть числами от 0.000001 до 1 000 000"
            );
        }
        
        this.a = a;
        this.b = b;
        this.h = h;
        this.result = result;
        this.id = counter++;
    }
    
    private boolean isValid(double x) {
        return x >= 0.000001 && x <= 1_000_000;
    }

    public double calculate() {
        if (h <= 0) {
            throw new IllegalArgumentException("Step must be > 0");
        }
        if (a <= 0 || b <= 0) {
            throw new IllegalArgumentException("1/x undefined at x <= 0");
        }
        double sum = 0.0;
        double x = a;
        while (x < b) {
            double x1 = x;
            double x2 = x + h;
            if (x2 > b) {
                x2 = b;
            }
            if (x1 == 0 || x2 == 0) {
                throw new IllegalArgumentException("1/x undefined at x = 0");
            }
            double step = x2 - x1;
            sum += (1.0 / x1 + 1.0 / x2) / 2.0 * step;
            x = x2;
        }
        result = sum;
        return sum;
    }
}

    
