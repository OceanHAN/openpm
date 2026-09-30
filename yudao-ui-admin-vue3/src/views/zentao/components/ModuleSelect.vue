<template>
  <!--
    模块树选择（禅道 zt_module）
    - (root, type, branch) 定位一棵树：需求/缺陷挂产品，任务挂执行
    - 树接口返回嵌套结构，直接用 el-tree-select
    - root 为空时控件不显示
  -->
  <el-tree-select
    v-if="visible"
    :model-value="modelValue"
    :data="treeData"
    :props="{ label: 'name', value: 'id', children: 'children' }"
    node-key="id"
    check-strictly
    clearable
    filterable
    :disabled="disabled"
    :placeholder="placeholder"
    class="w-full"
    @update:model-value="onChange"
  />
</template>

<script lang="ts" setup>
import * as ModuleApi from '@/api/zentao/module'

defineOptions({ name: 'ZentaoModuleSelect' })

const props = defineProps<{
  modelValue?: number
  /** 根对象：产品 id / 执行 id */
  root?: number
  /** 树类型：story / task / bug / case ... */
  type?: string
  /** 分支/平台，只有产品视图的树用得到 */
  branch?: number
  disabled?: boolean
}>()

const emit = defineEmits(['update:modelValue'])

const treeData = ref<ModuleApi.ModuleVO[]>([])
const visible = computed(() => !!props.root && !!props.type)
const placeholder = computed(() => '不选则挂在一级')

const load = async () => {
  if (!visible.value) {
    treeData.value = []
    return
  }
  treeData.value = await ModuleApi.getModuleTree({
    root: props.root!,
    type: props.type!,
    branch: props.branch
  })
}

// 根对象 / 类型 / 分支任一变化都要重新拉树，并清空已选模块
watch(
  () => [props.root, props.type, props.branch],
  async (_, old) => {
    await load()
    const changed = old && (old[0] !== props.root || old[1] !== props.type || old[2] !== props.branch)
    if (changed && props.modelValue) emit('update:modelValue', 0)
  },
  { immediate: true }
)

const onChange = (value: number | undefined) => emit('update:modelValue', value ?? 0)
</script>
