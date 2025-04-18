/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2025 BIOP
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package ch.epfl.biop.operetta.utils;

import java.util.Objects;
/**
 * Wrapper class to handle Fields, Channels, Z slices and Time points
 * Needed to produce a unique key for a given image
 */
public class FCZT {
    final int f;
    final int c;
    final int z;
    final int t;

    /**
     * Constructor
     * @param f Number of fields
     * @param c Number of channels
     * @param z Number of Z slices
     * @param t Number of time points
     */
    public FCZT(int f, int c, int z, int t) {
        this.f = f;
        this.c = c;
        this.z = z;
        this.t = t;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.f, this.c, this.z, this.t);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FCZT) {
            final FCZT o = (FCZT) obj;
            return (this.f == o.f && this.c == o.c && this.z == o.z && this.t == o.t);
        }
        return false;
    }
}
