package cdr.forms;

import crosswalk.InputField;

public class TextDepositField implements DepositField<String> {

	private String value;
	private InputField<?> formInputField;
	
	public String getValue() {
		return value;
	}
	
	public void setValue(String value) {
		this.value = value;
	}

	public InputField<?> getFormInputField() {
		return formInputField;
	}
	
	public void setFormInputField(InputField<?> formInputField) {
		this.formInputField = formInputField;
	}
	
}
