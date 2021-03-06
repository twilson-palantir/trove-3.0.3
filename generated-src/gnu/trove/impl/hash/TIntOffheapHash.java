///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
// Copyright (c) 2009, Rob Eden All Rights Reserved.
// Copyright (c) 2009, Jeff Randall All Rights Reserved.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package gnu.trove.impl.hash;

import gnu.trove.array.*;

import gnu.trove.procedure.TIntProcedure;
import gnu.trove.impl.HashFunctions;
import gnu.trove.impl.Constants;

//////////////////////////////////////////////////
// THIS IS A GENERATED CLASS. DO NOT HAND EDIT! //
//////////////////////////////////////////////////


/**
 * An open addressed hashing implementation for int primitives.
 *
 * Created: Sun Nov  4 08:56:06 2001
 *
 * @author Eric D. Friedman, Rob Eden, Jeff Randall
 * @version $Id: _E_OffheapHash.template,v 1.1.2.6 2009/11/07 03:36:44 robeden Exp $
 */
abstract public class TIntOffheapHash extends TPrimitiveOffheapHash {
	static final long serialVersionUID = 1L;

    /** the set of ints */
    public transient TIntOffheapArray _set;

    /**
     * value that represents null
     *
     * NOTE: should not be modified after the Hash is created, but is
     *       not final because of Externalization
     *
     */
    protected int no_entry_value;

    protected boolean consumeFreeSlot;

    
    /**
     * Creates a new <code>TIntHash</code> instance with the default
     * capacity and load factor.
     */
    public TIntOffheapHash() {
        this( DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR );
    }


    /**
     * Creates a new <code>TIntHash</code> instance with the default
     * capacity and load factor.
     */
    public TIntOffheapHash( int capacity ) {
        this( capacity, DEFAULT_LOAD_FACTOR );
    }


    /**
     * Creates a new <code>TIntOffheapHash</code> instance whose capacity
     * is the next highest prime above <tt>initialCapacity + 1</tt>
     * unless that value is already prime.
     *
     * @param initialCapacity an <code>int</code> value
     */
    public TIntOffheapHash( int initialCapacity, float load_factor ) {
        super( initialCapacity, load_factor );
        _set = new TIntOffheapArray( capacity() );
    }


    /**
     * Returns the value that is used to represent null. The default
     * value is generally zero, but can be changed during construction
     * of the collection.
     *
     * @return the value that represents null
     */
    public int getNoEntryValue() {
        return Constants.DEFAULT_INT_NO_ENTRY_VALUE;
    }


    /**
     * Searches the set for <tt>val</tt>
     *
     * @param val an <code>int</code> value
     * @return a <code>boolean</code> value
     */
    public boolean contains( int val ) {
        return index(val) >= 0;
    }


    /**
     * Executes <tt>procedure</tt> for each element in the set.
     *
     * @param procedure a <code>TObjectProcedure</code> value
     * @return false if the loop over the set terminated because
     * the procedure returned false for some value.
     */
    public boolean forEach( TIntProcedure procedure ) {
        TByteOffheapArray states = _states;
        TIntOffheapArray set = _set;
        for ( int i = capacity(); i-- > 0; ) {
            if ( states.get(i) == FULL && ! procedure.execute( set.get( i ) ) ) {
                return false;
            }
        }
        return true;
    }


    /**
     * Releases the element currently stored at <tt>index</tt>.
     *
     * @param index an <code>int</code> value
     */
    @Override
    protected void removeAt( int index ) {
        _set.put( index, no_entry_value );
        super.removeAt( index );
    }


    /**
     * Locates the index of <tt>val</tt>.
     *
     * @param val an <code>int</code> value
     * @return the index of <tt>val</tt> or -1 if it isn't in the set.
     */
    protected int index( int val ) {
        int hash, index, length;

        TByteOffheapArray states = _states;
        TIntOffheapArray set = _set;
        length = capacity();
        hash = HashFunctions.hash( val ) & 0x7fffffff;
        index = hash % length;
        byte state = states.get( index );

        if (state == FREE)
            return -1;

        if (state == FULL && set.get( index ) == val)
            return index;

        return indexRehashed(val, index, hash, state);
    }

    int indexRehashed(int key, int index, int hash, byte state) {
        // see Knuth, p. 529
        int length = capacity();
        int probe = 1 + (hash % (length - 2));
        final int loopIndex = index;

        do {
            index -= probe;
            if (index < 0) {
                index += length;
            }
            state = _states.get(index);
            //
            if (state == FREE)
                return -1;

            //
            if (key == _set.get( index ) && state != REMOVED)
                return index;
        } while (index != loopIndex);

        return -1;
    }

    /**
     * Locates the index at which <tt>val</tt> can be inserted.  if
     * there is already a value equal()ing <tt>val</tt> in the set,
     * returns that value as a negative integer.
     *
     * @param val an <code>int</code> value
     * @return an <code>int</code> value
     */
    protected int insertKey( int val ) {
        int hash, index;

        hash = HashFunctions.hash(val) & 0x7fffffff;
        index = hash % capacity();
        byte state = _states.get( index );

        consumeFreeSlot = false;

        if (state == FREE) {
            consumeFreeSlot = true;
            insertKeyAt(index, val);

            return index;       // empty, all done
        }

        if (state == FULL && _set.get( index ) == val) {
            return -index - 1;   // already stored
        }

        // already FULL or REMOVED, must probe
        return insertKeyRehash(val, index, hash, state);
    }

    int insertKeyRehash(int val, int index, int hash, byte state) {
        // compute the double hash
        final int length = capacity();
        int probe = 1 + (hash % (length - 2));
        final int loopIndex = index;
        int firstRemoved = -1;

        /**
         * Look until FREE slot or we start to loop
         */
        do {
            // Identify first removed slot
            if (state == REMOVED && firstRemoved == -1)
                firstRemoved = index;

            index -= probe;
            if (index < 0) {
                index += length;
            }
            state = _states.get( index );

            // A FREE slot stops the search
            if (state == FREE) {
                if (firstRemoved != -1) {
                    insertKeyAt(firstRemoved, val);
                    return firstRemoved;
                } else {
                    consumeFreeSlot = true;
                    insertKeyAt(index, val);
                    return index;
                }
            }

            if (state == FULL && _set.get( index ) == val) {
                return -index - 1;
            }

            // Detect loop
        } while (index != loopIndex);

        // We inspected all reachable slots and did not find a FREE one
        // If we found a REMOVED slot we return the first one found
        if (firstRemoved != -1) {
            insertKeyAt(firstRemoved, val);
            return firstRemoved;
        }

        // Can a resizing strategy be found that resizes the set?
        throw new IllegalStateException("No free or removed slots available. Key set full?!!");
    }

    void insertKeyAt(int index, int val) {
        _set.put( index, val );  // insert value
        _states.put( index, FULL );
    }

} // TIntOffheapHash
