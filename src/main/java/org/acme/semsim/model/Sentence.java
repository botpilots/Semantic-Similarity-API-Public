package org.acme.semsim.model;

import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a sentence with its text content and vector embedding.
 */
// TODO: Change name to ElementDuplicates, and ensure the following functionality:
// - Store Text objects having same text content
// - Store embedded vector for that text content
// - Store its similarity score coupled with the
//   associated textDuplicatesGroup id, in a hashmap. Retrievable with a get method.
// - Store if it were the standardMeasure for some
//   GroupId.
// - an uuid for each ElementDuplicates instance.
public class Sentence {
	private String text;
	private double[] vector;

	public Sentence() {
	}

	public Sentence(String text, double[] vector) {
		this.text = text;
		this.vector = vector;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public double[] getVector() {
		return vector;
	}

	public void setVector(double[] vector) {
		this.vector = vector;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Sentence sentence = (Sentence) o;
		return Objects.equals(text, sentence.text) && Arrays.equals(vector, sentence.vector);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(text);
		result = 31 * result + Arrays.hashCode(vector);
		return result;
	}

	@Override
	public String toString() {
		return "Sentence{" +
				"text='" + text + '\'' +
				", vector=" + (vector != null ? "length=" + vector.length : "null") +
				'}';
	}
}