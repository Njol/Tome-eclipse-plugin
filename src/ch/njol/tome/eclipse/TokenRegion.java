package ch.njol.tome.eclipse;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IRegion;

import ch.njol.tome.compiler.Token;

public class TokenRegion implements IRegion {
	
	public final Token token;
	public final int originalOffset;
	
	public TokenRegion(final Token token, final int originalOffset) {
		this.token = token;
		this.originalOffset = originalOffset;
	}
	
	@Override
	public int getOffset() {
		return token.absoluteRegionStart();
	}
	
	@Override
	public int getLength() {
		return token.regionLength();
	}
	
	@Override
	public boolean equals(@Nullable final Object o) {
		if (o instanceof IRegion) {
			final IRegion r = (IRegion) o;
			return r.getOffset() == getOffset() && r.getLength() == getLength();
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return (getOffset() << 24) | (getLength() << 16);
	}
	
}
