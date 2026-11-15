package br.com.ateliedigital.exceptions;

public class VioletedRule extends RuntimeException {
    public VioletedRule(String message) { super(message); }
}
