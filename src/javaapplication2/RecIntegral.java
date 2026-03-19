/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package javaapplication2;

public class RecIntegral {
    double a;
    double b;
    double h;
    double result;

    public RecIntegral(double a, double b, double h, double result){
        this.a = a;
        this.b = b;
        this.h = h;
        this.result = result;
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

    

