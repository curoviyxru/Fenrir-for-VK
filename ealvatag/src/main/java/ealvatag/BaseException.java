/*
 * Copyright (c) 2017 Eric A. Snell
 *
 * This file is part of eAlvaTag.
 *
 * eAlvaTag is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * eAlvaTag is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with eAlvaTag.  If not,
 * see <http://www.gnu.org/licenses/>.
 */

package ealvatag;

import java.util.Locale;

/**
 * Base exception which includes string formatting
 * <p>
 * Created by Eric A. Snell on 3/18/17.
 */
@SuppressWarnings("unused")
public class BaseException extends RuntimeException {
    public BaseException(String msg, Object... formatArgs) {
        super(String.format(Locale.getDefault(), msg, formatArgs));
    }

    public BaseException(Throwable cause, String msg, Object... formatArgs) {
        super(String.format(Locale.getDefault(), msg, formatArgs), cause);
    }
}
