/* Copyright 2017-18, Emmanouil Antonios Platanios. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.platanios.tensorflow.api.ops.variables

import org.platanios.tensorflow.api.core.{Graph, Shape}
import org.platanios.tensorflow.api.core.exception.{InvalidDataTypeException, ShapeMismatchException}
import org.platanios.tensorflow.api.ops.{Op, OpSpecification}
import org.platanios.tensorflow.api.ops.variables.Variable.VariableGetter
import org.platanios.tensorflow.api.types.DataType

/** Variable scope that carries default settings to provide to `getVariable`.
  *
  * A variable scope allows to create new variables and to share already created ones while providing checks to not
  * create or share by accident.
  *
  * Many of the arguments we need for `getVariable` in a variable store are most easily handled with a context.
  * [[VariableScope]] objects are used for the defaults.
  *
  * @param  reuse            [[Reuse]] value indicating whether to re-use an existing variable with the same name,
  *                          create a new variable, or do either.
  * @param  name             Name of the variable scope, used as a prefix in `getVariable`.
  * @param  initializer      Default initializer passed to `getVariable`.
  * @param  regularizer      Default regularizer passed to `getVariable`.
  * @param  partitioner      Default partitioner passed to `getVariable`.
  * @param  cachingDevice    Default caching device passed to `getVariable`.
  * @param  nameScope        Default name scope passed to `getVariable`.
  * @param  dataType         Default data type passed to `getVariable`.
  * @param  underlyingGetter Default underlying variable getter passed to `getVariable`.
  *
  * @author Emmanouil Antonios Platanios
  */
case class VariableScope private[variables](
    reuse: Reuse,
    name: String = "",
    dataType: DataType = null,
    initializer: Initializer = null,
    regularizer: Regularizer = null,
    partitioner: Partitioner = null,
    cachingDevice: OpSpecification => String = null,
    nameScope: String = "",
    underlyingGetter: VariableGetter = null
) {
  /** Gets an existing variable with the specified name or creates a new one.
    *
    * @param  store         Variable store currently being used to store variables.
    * @param  name          Variable name.
    * @param  dataType      Variable data type.
    * @param  shape         Variable shape.
    * @param  initializer   Variable initializer. If `initializer` is `null` (the default), the default initializer
    *                       passed in the constructor is used. If that one is `null` too, then we use a new
    *                       `glorotUniformInitializer`. The initializer will be called for each part of the partitioned
    *                       variable separately.
    * @param  regularizer   Variable regularizer.
    * @param  trainable     If `true`, the default, the variable is added to the graph collection
    *                       `Graph.Keys.TRAINABLE_VARIABLES`. This collection is used as the default set of variables
    *                       to use by the optimizers.
    * @param  reuse         [[Reuse]] value indicating whether to re-use an existing variable with the same name, create
    *                       a new variable, or do either.
    *                       - If `reuse` is `null` (the default), both new and existing variables are returned.
    * @param  collections   Set of graph collections keys. The variable is added to these collections. Defaults to
    *                       `Set(Graph.Keys.GLOBAL_VARIABLES)`.
    * @param  cachingDevice Device specification describing where the variable should be cached for reading. Defaults
    *                       to the variable's device. Typical use is to cache on the device where the ops using the
    *                       variable reside, to deduplicate copying through `Switch` and other conditional statements.
    * @return Requested variable.
    * @throws IllegalArgumentException If any of the provided arguments are not compatible with each other, or with the
    *                                  variables stored in this variable store.
    * @throws ShapeMismatchException   If the provided shape does not match the shape of the corresponding variable
    *                                  stored in this variable store (if there exists one).
    * @throws InvalidDataTypeException If the provided data type does not match the data type of the corresponding
    *                                  variable stored in this variable store (if there exists one).
    */
  @throws[IllegalArgumentException]
  @throws[ShapeMismatchException]
  @throws[InvalidDataTypeException]
  def getVariable(
      store: VariableStore,
      name: String,
      dataType: DataType = this.dataType,
      shape: Shape = null,
      initializer: Initializer = this.initializer,
      regularizer: Regularizer = this.regularizer,
      trainable: Boolean = true,
      reuse: Reuse = this.reuse,
      collections: Set[Graph.Key[Variable]] = Set.empty,
      cachingDevice: OpSpecification => String = this.cachingDevice
  ): Variable = {
    val fullName = if (this.name != null && this.name != "") s"${this.name}/$name" else name
    // Variable names only depend on the variable scope and not the name scope, so we reset it below for the time of
    // variable creation.
    Op.createWith(nameScope = "") {
      store.getVariable(
        fullName, dataType, shape, initializer, regularizer, trainable, reuse, collections, cachingDevice)
    }
  }

  /** Gets an existing partitioned variable with the specified name or creates a new one.
    *
    * @param  store         Variable store currently being used to store variables.
    * @param  name          Variable name.
    * @param  dataType      Variable data type.
    * @param  shape         Variable shape.
    * @param  initializer   Variable initializer. If `initializer` is `null` (the default), the default initializer
    *                       passed in the constructor is used. If that one is `null` too, then we use a new
    *                       `glorotUniformInitializer`. The initializer will be called for each part of the partitioned
    *                       variable separately.
    * @param  regularizer   Variable regularizer.
    * @param  partitioner   Function that accepts a fully defined `Shape` and returns a sequence of integers (i.e., the
    *                       `partitions`). These integers describe how to partition the given variable, along the each
    *                       dimension. That is, `partitions(1) = 3` means that we split the variable into `3` parts
    *                       along dimension `1`. Currently, partitioning along only a single axis is supported.
    * @param  trainable     If `true`, the default, the variable is added to the graph collection
    *                       `Graph.Keys.TRAINABLE_VARIABLES`. This collection is used as the default set of variables
    *                       to use by the optimizers.
    * @param  reuse         [[Reuse]] value indicating whether to re-use an existing variable with the same name, create
    *                       a new variable, or do either.
    * @param  collections   Set of graph collections keys. The variable is added to these collections. Defaults to
    *                       `Set(Graph.Keys.GLOBAL_VARIABLES)`.
    * @param  cachingDevice Device specification describing where the variable should be cached for reading. Defaults
    *                       to the variable's device. Typical use is to cache on the device where the ops using the
    *                       variable reside, to deduplicate copying through `Switch` and other conditional statements.
    * @return Requested variable.
    * @throws IllegalArgumentException If any of the provided arguments are not compatible with each other, or with the
    *                                  variables stored in this variable store.
    * @throws ShapeMismatchException   If the provided shape does not match the shape of the corresponding variable
    *                                  stored in this variable store (if there exists one).
    * @throws InvalidDataTypeException If the provided data type does not match the data type of the corresponding
    *                                  variable stored in this variable store (if there exists one).
    */
  @throws[IllegalArgumentException]
  @throws[ShapeMismatchException]
  @throws[InvalidDataTypeException]
  def getPartitionedVariable(
      store: VariableStore,
      name: String,
      dataType: DataType = this.dataType,
      shape: Shape = null,
      initializer: Initializer = this.initializer,
      regularizer: Regularizer = this.regularizer,
      partitioner: Partitioner = this.partitioner,
      trainable: Boolean = true,
      reuse: Reuse = this.reuse,
      collections: Set[Graph.Key[Variable]] = Set.empty,
      cachingDevice: OpSpecification => String = this.cachingDevice
  ): PartitionedVariable = {
    if (underlyingGetter != null)
      throw new IllegalArgumentException(
        "Private access to 'getPartitionedVariable' is not allowed when a custom getter is set.")
    if (partitioner == null)
      throw new IllegalArgumentException("No partitioner was specified.")
    val fullName = if (this.name != null && this.name != "") s"${this.name}/$name" else name
    // Variable names only depend on the variable scope and not the name scope, so we reset it below for the time of
    // variable creation.
    Op.createWith(nameScope = "") {
      store.getPartitionedVariable(
        fullName, dataType, shape, initializer, regularizer, partitioner, trainable, reuse, collections, cachingDevice)
    }
  }
}

private[api] object VariableScope {
  /** Returns the current variable scope. */
  def current: VariableScope = VariableScopeStore.current.scope

  /** Sets the variable scope to use for op creation context, for all code in `block`.
    *
    * @param  name             Variable scope name, that may also change the name scope of the op creation context,
    *                          depending on the value of `isPure`.
    * @param  reuse            [[Reuse]] value indicating whether to re-use an existing variable with the same name, or
    *                          do either. Note that this argument cannot be set to [[CreateNewOnly]] in this function.
    *                          If set to [[ReuseOrCreateNew]], then the parent variable scope `reuse` value is used
    *                          (i.e., propagated).
    * @param  dataType         Default data type for variables within the scope.
    * @param  initializer      Default initializer for variables within the scope.
    * @param  regularizer      Default regularizer for variables within the scope.
    * @param  partitioner      Default partitioner for variables within the scope.
    * @param  cachingDevice    Default caching device for variables within the scope.
    * @param  underlyingGetter Default variable getter for variables within the scope.
    * @param  isDefaultName    Boolean value indicating whether `name` is a default name or not. If `true`, then `name`
    *                          will be made unique before being used. `isDefaultName` cannot be set to `true` when
    *                          `reuse` is set to [[ReuseExistingOnly]].
    * @param  isPure           Boolean value indicating whether to use a "pure" variable scope. That is, a variable
    *                          scope that does not affect the name scope of the current op creation context.
    * @param  block            Code block to run using the provided options.
    * @tparam R Return type of the code block.
    * @return Return value of the code block.
    */
  private[api] def scope[R](
      name: String,
      reuse: Reuse = ReuseOrCreateNew,
      dataType: DataType = null,
      initializer: Initializer = null,
      regularizer: Regularizer = null,
      partitioner: Partitioner = null,
      cachingDevice: OpSpecification => String = null,
      underlyingGetter: VariableGetter = null,
      isDefaultName: Boolean = false,
      isPure: Boolean = false
  )(block: => R): R = {
    if (reuse == ReuseExistingOnly && isDefaultName)
      throw new IllegalArgumentException(
        "'reuse' cannot be set to 'ReuseExistingOnly' with 'isDefaultName' set to 'true'.")
    val variableScopeStore = VariableScopeStore.current
    val oldVariableScope = variableScopeStore.scope
    val newName = {
      val uniqueName = if (isDefaultName) VariableScope.unique(name) else name
      if (oldVariableScope.name != null && oldVariableScope.name != "")
        s"${oldVariableScope.name}/$uniqueName"
      else
        uniqueName
    }
    variableScopeStore.enterVariableScope(newName)
    val newVariableScope = VariableScope(
      reuse = if (reuse == ReuseOrCreateNew) oldVariableScope.reuse else reuse,
      name = newName,
      dataType = if (dataType == null) oldVariableScope.dataType else dataType,
      initializer = if (initializer == null) oldVariableScope.initializer else initializer,
      regularizer = if (regularizer == null) oldVariableScope.regularizer else regularizer,
      partitioner = if (partitioner == null) oldVariableScope.partitioner else partitioner,
      cachingDevice = if (cachingDevice == null) oldVariableScope.cachingDevice else cachingDevice,
      nameScope = name,
      underlyingGetter = {
        if (underlyingGetter == null)
          oldVariableScope.underlyingGetter
        else
          maybeWrapCustomVariableGetter(underlyingGetter, oldVariableScope.underlyingGetter)
      })
    variableScopeStore.scope = newVariableScope
    val result = if (isPure) block else Op.createWithNameScope(name)(block)
    variableScopeStore.closeVariableSubScopes(newName)
    variableScopeStore.scope = oldVariableScope
    result
  }

  /** Sets the variable scope to use for op creation context, for all code in `block`.
    *
    * @param  variableScope    Default variable scope to use. Other arguments of this function can override the
    *                          corresponding parameters of `variableScope`.
    * @param  reuse            [[Reuse]] value indicating whether to re-use an existing variable with the same name, or
    *                          do either. Note that this argument cannot be set to [[CreateNewOnly]] in this function.
    *                          If set to [[ReuseOrCreateNew]], then the parent variable scope `reuse` value is used
    *                          (i.e., propagated).
    * @param  dataType         Default data type for variables within the scope.
    * @param  initializer      Default initializer for variables within the scope.
    * @param  regularizer      Default regularizer for variables within the scope.
    * @param  partitioner      Default partitioner for variables within the scope.
    * @param  cachingDevice    Default caching device for variables within the scope.
    * @param  underlyingGetter Default variable getter for variables within the scope.
    * @param  isPure           Boolean value indicating whether to use a "pure" variable scope. That is, a variable
    *                          scope that does not affect the name scope of the current op creation context.
    * @param  block            Code block to run using the provided options.
    * @tparam R Return type of the code block.
    * @return Return value of the code block.
    */
  private[api] def updatedScope[R](
      variableScope: VariableScope = VariableScope.current,
      reuse: Reuse = ReuseOrCreateNew,
      dataType: DataType = null,
      initializer: Initializer = null,
      regularizer: Regularizer = null,
      partitioner: Partitioner = null,
      cachingDevice: OpSpecification => String = null,
      underlyingGetter: VariableGetter = null,
      isPure: Boolean = false
  )(block: => R): R = {
    val variableScopeStore = VariableScopeStore.current
    val oldVariableScope = variableScopeStore.scope
    val oldVariableScopeCounts = variableScopeStore.variableScopeCounts
    variableScopeStore.enterVariableScope(variableScope.name)
    val newVariableScope = VariableScope(
      reuse = if (reuse == ReuseOrCreateNew) variableScope.reuse else reuse,
      name = variableScope.name,
      dataType = if (dataType == null) variableScope.dataType else dataType,
      initializer = if (initializer == null) variableScope.initializer else initializer,
      regularizer = if (regularizer == null) variableScope.regularizer else regularizer,
      partitioner = if (partitioner == null) variableScope.partitioner else partitioner,
      cachingDevice = if (cachingDevice == null) variableScope.cachingDevice else cachingDevice,
      nameScope = variableScope.nameScope,
      underlyingGetter = {
        if (underlyingGetter == null)
          variableScope.underlyingGetter
        else
          maybeWrapCustomVariableGetter(underlyingGetter, variableScope.underlyingGetter)
      })
    variableScopeStore.scope = newVariableScope
    val result = if (isPure) block else Op.createWithNameScope(variableScope.name.split("/").last)(block)
    variableScopeStore.closeVariableSubScopes(variableScope.name)
    variableScopeStore.variableScopeCounts = oldVariableScopeCounts
    variableScopeStore.scope = oldVariableScope
    result
  }

  /** If a new getter is provided, it wraps around the old one and the new wrapped getter is returned. Otherwise, the
    * old getter is returned.
    *
    * @param  getter    New variable getter.
    * @param  oldGetter Old variable getter.
    * @return Variable getter to use.
    */
  private[api] def maybeWrapCustomVariableGetter(
      getter: VariableGetter,
      oldGetter: VariableGetter
  ): VariableGetter = {
    if (getter == null) {
      oldGetter
    } else {
      new VariableGetter {
        override def apply(
            name: String,
            dataType: DataType,
            shape: Shape,
            initializer: Initializer,
            regularizer: Regularizer,
            trainable: Boolean,
            reuse: Reuse,
            collections: Set[Graph.Key[Variable]],
            cachingDevice: OpSpecification => String,
            underlyingGetter: VariableGetter
        ): Variable = {
          val baseGetter: VariableGetter = new VariableGetter {
            override def apply(
                name: String,
                dataType: DataType,
                shape: Shape,
                initializer: Initializer,
                regularizer: Regularizer,
                trainable: Boolean,
                reuse: Reuse,
                collections: Set[Graph.Key[Variable]],
                cachingDevice: OpSpecification => String,
                underlyingGetter: VariableGetter
            ): Variable = {
              oldGetter(
                name, dataType, shape, initializer, regularizer, trainable, reuse, collections, cachingDevice,
                underlyingGetter)
            }
          }
          getter(
            name, dataType, shape, initializer, regularizer, trainable, reuse, collections, cachingDevice, baseGetter)
        }
      }
    }
  }

  /** Gets a name with the provided prefix that is unique in the current variable scope.
    *
    * @param  prefix Prefix.
    * @return Unique name with the provided prefix.
    */
  private[api] def unique(prefix: String): String = {
    val currentScopeStore = VariableScopeStore.current
    val currentScope = Op.convertNameScopeToName(VariableScope.current.name)
    val name = {
      if (currentScope == null || currentScope == "")
        prefix
      else
        s"$currentScope/$prefix"
    }
    if (currentScopeStore.variableScopeCount(name) == 0) {
      prefix
    } else {
      var uniqueName = name
      var count = 1
      while (currentScopeStore.variableScopeCount(uniqueName) > 0) {
        uniqueName = s"${name}_$count"
        count += 1
      }
      uniqueName
    }
  }
}
