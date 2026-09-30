<template>
  <!--
    分支/平台下拉（禅道 zt_branch）
    - 产品类型为 branch 时叫「分支」，platform 时叫「平台」，normal 时整个控件不显示
    - id=0 是虚拟主干，一定出现在选项里
    - 产品变化时自动重载并清空已选值
  -->
  <el-select
    v-if="visible"
    :model-value="modelValue"
    :placeholder="placeholder"
    clearable
    filterable
    :disabled="disabled"
    class="w-full"
    @update:model-value="onChange"
  >
    <el-option :label="mainLabel" :value="0" />
    <el-option v-for="b in branchList" :key="b.id" :label="b.name" :value="b.id!">
      <span>{{ b.name }}</span>
      <el-tag v-if="b.defaultFlag === 1" size="small" class="ml-8px" type="success">默认</el-tag>
      <el-tag v-if="b.status === 'closed'" size="small" class="ml-8px" type="info">已关闭</el-tag>
    </el-option>
  </el-select>
</template>

<script lang="ts" setup>
import * as BranchApi from '@/api/zentao/branch'
import * as ProductApi from '@/api/zentao/product'
import { isBranchAware } from '@/views/zentao/module/constants'

defineOptions({ name: 'ZentaoBranchSelect' })

const props = defineProps<{
  modelValue?: number
  /** 产品编号。为空时控件不显示 */
  product?: number
  /** 树类型：story / bug / case 才需要分支 */
  type?: string
  disabled?: boolean
}>()

const emit = defineEmits(['update:modelValue'])

const branchList = ref<BranchApi.BranchVO[]>([])
const branchLabel = ref('分支')
const visible = ref(false)

const placeholder = computed(() => `请选择${branchLabel.value}`)
const mainLabel = computed(() => `主干（默认${branchLabel.value}）`)

const load = async () => {
  branchList.value = []
  visible.value = false
  if (!props.product || !isBranchAware(props.type)) return
  // 产品类型决定「分支」还是「平台」：normal 类型没有分支，控件整体隐藏
  const products = await ProductApi.getProductSimpleList()
  const product = products.find((p) => p.id === props.product)
  if (!product || (product.type !== 'branch' && product.type !== 'platform')) return
  branchLabel.value = product.type === 'platform' ? '平台' : '分支'
  branchList.value = await BranchApi.getBranchListByProduct(props.product)
  visible.value = true
}

// 切换产品：清掉旧的分支值，否则会把上一个产品的分支 id 带过去
watch(
  () => [props.product, props.type],
  async (_, old) => {
    await load()
    if (old && old[0] !== props.product && props.modelValue) emit('update:modelValue', 0)
  },
  { immediate: true }
)

const onChange = (value: number | undefined) => emit('update:modelValue', value ?? 0)
</script>
