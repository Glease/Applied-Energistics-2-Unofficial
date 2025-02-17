/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.guisync;

import appeng.container.AEBaseContainer;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketProgressBar;
import appeng.core.sync.packets.PacketValueConfig;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.EnumSet;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ICrafting;

public class SyncData {

    private final AEBaseContainer source;
    private final Field[] indirections;
    private final Field field;
    private final String fieldName;
    private final int channel;
    private Object clientVersion;

    public SyncData(final AEBaseContainer container, final Field field, final GuiSync annotation) {
        this(container, new Field[0], field, annotation.value());
    }

    public SyncData(final AEBaseContainer container, final Field[] indirections, final Field field, final int channel) {
        this.clientVersion = null;
        this.source = container;
        this.indirections = indirections;
        this.field = field;
        this.channel = channel;
        StringBuilder nameBuilder = new StringBuilder();
        for (Field indirection : this.indirections) {
            nameBuilder.append(indirection.getName());
            nameBuilder.append('.');
        }
        nameBuilder.append(this.field.getName());
        this.fieldName = nameBuilder.toString();
    }

    public int getChannel() {
        return this.channel;
    }

    private Object getValue() throws IllegalAccessException {
        Object currentObject = source;
        for (Field indirection : indirections) {
            currentObject = indirection.get(currentObject);
        }
        return field.get(currentObject);
    }

    private void setValue(Object newVal) throws IllegalAccessException {
        Object currentObject = source;
        for (Field indirection : indirections) {
            currentObject = indirection.get(currentObject);
        }
        field.set(currentObject, newVal);
    }

    public void tick(final ICrafting c) {
        try {
            final Object val = getValue();
            if (val != null && this.clientVersion == null) {
                this.send(c, val);
            } else if (!val.equals(this.clientVersion)) {
                this.send(c, val);
            }
        } catch (final IllegalArgumentException e) {
            AELog.debug(e);
        } catch (final IllegalAccessException e) {
            AELog.debug(e);
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    private void send(final ICrafting o, final Object val) throws IOException {
        if (val instanceof String) {
            if (o instanceof EntityPlayerMP) {
                NetworkHandler.instance.sendTo(
                        new PacketValueConfig("SyncDat." + this.channel, (String) val), (EntityPlayerMP) o);
            }
        } else if (this.field.getType().isEnum()) {
            o.sendProgressBarUpdate(this.source, this.channel, ((Enum) val).ordinal());
        } else if (val instanceof Long || val.getClass() == long.class) {
            NetworkHandler.instance.sendTo(new PacketProgressBar(this.channel, (Long) val), (EntityPlayerMP) o);
        } else if (val instanceof Boolean || val.getClass() == boolean.class) {
            o.sendProgressBarUpdate(this.source, this.channel, ((Boolean) val) ? 1 : 0);
        } else {
            o.sendProgressBarUpdate(this.source, this.channel, (Integer) val);
        }

        this.clientVersion = val;
    }

    public void update(final Object val) {
        try {
            final Object oldValue = this.getValue();
            if (val instanceof String) {
                this.updateString(oldValue, (String) val);
            } else {
                this.updateValue(oldValue, (Long) val);
            }
        } catch (final IllegalArgumentException e) {
            AELog.debug(e);
        } catch (final IllegalAccessException e) {
            AELog.debug(e);
        }
    }

    private void updateString(final Object oldValue, final String val) {
        try {
            this.setValue(val);
            this.source.onUpdate(this.fieldName, oldValue, this.getValue());
        } catch (final IllegalArgumentException e) {
            AELog.debug(e);
        } catch (final IllegalAccessException e) {
            AELog.debug(e);
        }
    }

    private void updateValue(final Object oldValue, final long val) {
        try {
            if (this.field.getType().isEnum()) {
                final EnumSet<? extends Enum> valList = EnumSet.allOf((Class<? extends Enum>) this.field.getType());
                for (final Enum e : valList) {
                    if (e.ordinal() == val) {
                        this.setValue(e);
                        break;
                    }
                }
            } else {
                if (this.field.getType().equals(int.class)) {
                    this.setValue((int) val);
                } else if (this.field.getType().equals(long.class)) {
                    this.setValue(val);
                } else if (this.field.getType().equals(boolean.class)) {
                    this.setValue(val == 1);
                } else if (this.field.getType().equals(Integer.class)) {
                    this.setValue((int) val);
                } else if (this.field.getType().equals(Long.class)) {
                    this.setValue(val);
                } else if (this.field.getType().equals(Boolean.class)) {
                    this.setValue(val == 1);
                }
            }

            this.source.onUpdate(this.fieldName, oldValue, this.getValue());
        } catch (final IllegalArgumentException e) {
            AELog.debug(e);
        } catch (final IllegalAccessException e) {
            AELog.debug(e);
        }
    }
}
