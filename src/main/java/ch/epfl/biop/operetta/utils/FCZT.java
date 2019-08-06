package ch.epfl.biop.operetta.utils;

import java.util.Objects;

public class FCZT {
    int f;
    int c;
    int z;
    int t;


    public FCZT( int f, int c, int z, int t ) {
        this.f = f;
        this.c = c;
        this.z = z;
        this.t = t;
    }

    @Override
    public int hashCode( ) {
        return Objects.hash( this.f, this.c, this.z, this.t );
    }

    @Override
    public boolean equals( Object obj ) {
        if (obj instanceof FCZT ) {
            final FCZT o = (FCZT) obj;
            return ( this.f == o.f && this.c == o.c && this.z == o.z && this.t == o.t );
        }
        return false;
    }
}
