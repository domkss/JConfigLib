/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2025 Dominik Kiss
 * Repository: https://github.com/domkss/JConfigLib
 */

package dev.domkss.jconfiglib;

import java.lang.annotation.*;

/**
 * The annotated field will be included in the config file with default value if not already present
 * Use the `comment` parameter to add descriptive notes.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface ConfigField {
    /**
     * Optional comment to describe the field purpose in the YAML file.
     *
     * @return the comment string (default is empty)
     */
    String comment()  default "";
}