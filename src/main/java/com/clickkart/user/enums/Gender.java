// src/main/java/com/clickkart/user/enums/Gender.java
package com.clickkart.user.enums;

/**
 * Optional self-declared profile field. {@link #PREFER_NOT_TO_SAY} is a real, selectable value
 * rather than leaving {@code null} as the only way to decline - a customer who actively chooses
 * not to answer is not the same as one who never reached the field, and collapsing the two would
 * make the platform re-prompt someone who already said no.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER,
    PREFER_NOT_TO_SAY
}
